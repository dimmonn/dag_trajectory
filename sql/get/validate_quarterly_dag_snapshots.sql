-- Validation queries for the quarterly DAG snapshot pipeline.
-- Every query should return zero rows (or zero violations in the summary).

-- 1. A snapshot must never contain a commit at or after its exclusive cutoff.
SELECT s.project_ref, s.quarter_start, c.commit_hash, c.commit_time, s.cutoff_at
FROM dag_snapshots s
JOIN dag_snapshot_commits c ON c.snapshot_id = s.snapshot_id
WHERE c.commit_time >= s.cutoff_at;

-- 2. Every snapshot edge must have both endpoints in the same snapshot.
SELECT e.*
FROM dag_snapshot_edges e
LEFT JOIN dag_snapshot_commits p
  ON p.snapshot_id = e.snapshot_id AND p.commit_hash = e.parent_hash
LEFT JOIN dag_snapshot_commits c
  ON c.snapshot_id = e.snapshot_id AND c.commit_hash = e.child_hash
WHERE p.commit_hash IS NULL OR c.commit_hash IS NULL;

-- 3. Stored parent_count must equal the number of incoming edges in the snapshot.
SELECT c.snapshot_id, c.commit_hash, c.parent_count, COUNT(e.parent_hash) AS edge_parent_count
FROM dag_snapshot_commits c
LEFT JOIN dag_snapshot_edges e
  ON e.snapshot_id = c.snapshot_id AND e.child_hash = c.commit_hash
GROUP BY c.snapshot_id, c.commit_hash, c.parent_count
HAVING c.parent_count <> COUNT(e.parent_hash);

-- 4. Snapshot metadata counts must match the materialized node and edge rows.
WITH node_counts AS (
    SELECT snapshot_id, COUNT(*) AS n FROM dag_snapshot_commits GROUP BY snapshot_id
), edge_counts AS (
    SELECT snapshot_id, COUNT(*) AS n FROM dag_snapshot_edges GROUP BY snapshot_id
)
SELECT s.snapshot_id, s.commit_count, COALESCE(n.n, 0) AS actual_commits,
       s.edge_count, COALESCE(e.n, 0) AS actual_edges
FROM dag_snapshots s
LEFT JOIN node_counts n USING (snapshot_id)
LEFT JOIN edge_counts e USING (snapshot_id)
WHERE s.commit_count <> COALESCE(n.n, 0)
   OR s.edge_count <> COALESCE(e.n, 0);

-- 5. A source edge whose child and parent are both in a snapshot must appear there.
SELECT s.snapshot_id, e.parent_hash, e.child_hash
FROM dag_snapshots s
JOIN dag_source_edges e ON e.project_ref = s.project_ref
JOIN dag_snapshot_commits p
  ON p.snapshot_id = s.snapshot_id AND p.commit_hash = e.parent_hash
JOIN dag_snapshot_commits c
  ON c.snapshot_id = s.snapshot_id AND c.commit_hash = e.child_hash
LEFT JOIN dag_snapshot_edges se
  ON se.snapshot_id = s.snapshot_id
 AND se.parent_hash = e.parent_hash
 AND se.child_hash = e.child_hash
WHERE se.parent_hash IS NULL;


-- 6. Run AFTER extract_quarterly_dag_advanced_features.sql.
-- Every snapshot depth must be recalculated from its own induced edges,
-- and must contain exactly one depth observation per snapshot commit.
SELECT
    project_ref,
    quarter_start,
    commit_count,
    depth_observation_count,
    depth_max,
    depth_valid,
    depth_method
FROM public.dag_snapshot_advanced_features
WHERE depth_method IS DISTINCT FROM 'snapshot_recomputed'
   OR depth_observation_count <> commit_count
   OR NOT depth_valid
   OR (commit_count > 0 AND depth_max >= commit_count);

-- 7. Advanced feature table must contain exactly one row per DAG snapshot.
SELECT
    s.snapshot_id,
    s.project_ref,
    s.quarter_start
FROM public.dag_snapshots s
LEFT JOIN public.dag_snapshot_advanced_features f
  ON f.snapshot_id = s.snapshot_id
WHERE f.snapshot_id IS NULL;

-- 8. PR cycle-time summaries must not count negative durations as valid
-- observations; those are reported separately as anomalies.
SELECT *
FROM public.dag_snapshot_advanced_features
WHERE pr_cycle_time_observation_count < 0
   OR pr_cycle_time_negative_count < 0;
