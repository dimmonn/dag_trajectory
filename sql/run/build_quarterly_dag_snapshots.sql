-- =========================================================
-- 1. Store the original commit DAG nodes
-- =========================================================

CREATE TABLE IF NOT EXISTS dag_source_commits (
                                                  project_ref  text NOT NULL,
                                                  commit_hash  text NOT NULL,
                                                  commit_time  timestamptz NOT NULL,
                                                  parents      jsonb NOT NULL DEFAULT '[]'::jsonb,

                                                  PRIMARY KEY (project_ref, commit_hash)
);

-- =========================================================
-- 2. Store DAG edges: parent -> child
-- =========================================================

CREATE TABLE IF NOT EXISTS dag_source_edges (
                                                project_ref text NOT NULL,
                                                parent_hash text NOT NULL,
                                                child_hash  text NOT NULL,

                                                PRIMARY KEY (project_ref, parent_hash, child_hash),

                                                FOREIGN KEY (project_ref, parent_hash)
                                                    REFERENCES dag_source_commits(project_ref, commit_hash),

                                                FOREIGN KEY (project_ref, child_hash)
                                                    REFERENCES dag_source_commits(project_ref, commit_hash)
);

CREATE INDEX IF NOT EXISTS idx_dag_source_edges_child
    ON dag_source_edges(project_ref, child_hash);

-- =========================================================
-- 3. Store quarterly snapshot metadata
-- cutoff_at is exclusive:
-- Q1 2024 includes commits before 2024-04-01 00:00 UTC.
-- =========================================================

CREATE TABLE IF NOT EXISTS dag_snapshots (
                                             snapshot_id  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                             project_ref  text NOT NULL,
                                             quarter_start date NOT NULL,
                                             cutoff_at    timestamptz NOT NULL,
                                             commit_count bigint NOT NULL DEFAULT 0,
                                             edge_count   bigint NOT NULL DEFAULT 0,
                                             generated_at timestamptz NOT NULL DEFAULT now(),

                                             UNIQUE (project_ref, quarter_start)
);

-- =========================================================
-- 4. Store commits belonging to each snapshot
-- =========================================================

CREATE TABLE IF NOT EXISTS dag_snapshot_commits (
                                                    snapshot_id bigint NOT NULL
                                                        REFERENCES dag_snapshots(snapshot_id) ON DELETE CASCADE,
                                                    commit_hash text NOT NULL,
                                                    commit_time timestamptz NOT NULL,
                                                    parent_count integer NOT NULL DEFAULT 0,

                                                    PRIMARY KEY (snapshot_id, commit_hash)
);

CREATE INDEX IF NOT EXISTS idx_dag_snapshot_commits_time
    ON dag_snapshot_commits(snapshot_id, commit_time);

-- =========================================================
-- 5. Store edges belonging to each snapshot
-- Both parent and child must exist in that snapshot.
-- =========================================================

CREATE TABLE IF NOT EXISTS dag_snapshot_edges (
                                                  snapshot_id bigint NOT NULL,
                                                  parent_hash text NOT NULL,
                                                  child_hash  text NOT NULL,

                                                  PRIMARY KEY (snapshot_id, parent_hash, child_hash),

                                                  FOREIGN KEY (snapshot_id, parent_hash)
                                                      REFERENCES dag_snapshot_commits(snapshot_id, commit_hash)
                                                      ON DELETE CASCADE,

                                                  FOREIGN KEY (snapshot_id, child_hash)
                                                      REFERENCES dag_snapshot_commits(snapshot_id, commit_hash)
                                                      ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_dag_snapshot_edges_child
    ON dag_snapshot_edges(snapshot_id, child_hash);


-- =========================================================
-- DATA LOADING: refresh the derived DAG snapshot tables
-- This does not modify your existing application tables.
-- =========================================================

TRUNCATE TABLE dag_snapshot_edges,
               dag_snapshot_commits,
               dag_snapshots
RESTART IDENTITY;

TRUNCATE TABLE dag_source_edges,
               dag_source_commits;


-- =========================================================
-- 6. Extract nodes from project_graphs.graph_json
--
-- Only original graphs are used. Graphs already stored as
-- subgraphs are excluded via source_project_ref IS NULL.
--
-- The JSON field authoredAtEpochSecond is used as stored.
-- =========================================================

INSERT INTO dag_source_commits (
    project_ref,
    commit_hash,
    commit_time,
    parents
)
SELECT
    pg.project_ref,
    node.commit_json ->> 'hash',
    to_timestamp(
            (node.commit_json ->> 'authoredAtEpochSecond')::bigint
    ),
    CASE
        WHEN jsonb_typeof(node.commit_json -> 'parents') = 'array'
            THEN node.commit_json -> 'parents'
        ELSE '[]'::jsonb
        END
FROM project_graphs pg
         CROSS JOIN LATERAL jsonb_array_elements(
    CASE
        WHEN jsonb_typeof(pg.graph_json::jsonb -> 'commits') = 'array'
            THEN pg.graph_json::jsonb -> 'commits'
        ELSE '[]'::jsonb
    END
) AS node(commit_json)
WHERE pg.source_project_ref IS NULL
  AND node.commit_json ->> 'hash' IS NOT NULL
  AND node.commit_json ->> 'authoredAtEpochSecond' IS NOT NULL
ON CONFLICT (project_ref, commit_hash) DO NOTHING;


-- =========================================================
-- 7. Extract parent-child edges
-- Missing parent nodes are ignored.
-- =========================================================

INSERT INTO dag_source_edges (
    project_ref,
    parent_hash,
    child_hash
)
SELECT
    child.project_ref,
    parent_ref.parent_hash,
    child.commit_hash
FROM dag_source_commits child
         CROSS JOIN LATERAL jsonb_array_elements_text(
    child.parents
) AS parent_ref(parent_hash)
JOIN dag_source_commits parent
ON parent.project_ref = child.project_ref
    AND parent.commit_hash = parent_ref.parent_hash
ON CONFLICT (project_ref, parent_hash, child_hash) DO NOTHING;


-- =========================================================
-- 8. Generate quarter-end snapshots through the latest
-- completed quarter, using UTC.
-- =========================================================

WITH project_bounds AS (
    SELECT
        project_ref,
        MIN(commit_time AT TIME ZONE 'UTC')::date AS first_commit_date
    FROM dag_source_commits
    GROUP BY project_ref
),
     quarter_bounds AS (
         SELECT
             project_ref,
             DATE_TRUNC('quarter', first_commit_date)::date AS first_quarter,
             (
                 DATE_TRUNC(
                         'quarter',
                         CURRENT_TIMESTAMP AT TIME ZONE 'UTC'
                 ) - INTERVAL '3 months'
                 )::date AS last_complete_quarter
         FROM project_bounds
     ),
     quarters AS (
         SELECT
             b.project_ref,
             g.quarter_start::date AS quarter_start
         FROM quarter_bounds b
                  CROSS JOIN LATERAL GENERATE_SERIES(
                 b.first_quarter::timestamp,
                 b.last_complete_quarter::timestamp,
                 INTERVAL '3 months'
                                     ) AS g(quarter_start)
         WHERE b.first_quarter <= b.last_complete_quarter
     )
INSERT INTO dag_snapshots (
    project_ref,
    quarter_start,
    cutoff_at
)
SELECT
    project_ref,
    quarter_start,
    (quarter_start + INTERVAL '3 months') AT TIME ZONE 'UTC'
FROM quarters
ON CONFLICT (project_ref, quarter_start) DO NOTHING;

-- =========================================================
-- 9. Populate each snapshot with commits that existed
-- according to the stored commit timestamp at its cutoff.
-- parent_count counts only parents included in that snapshot.
-- =========================================================

INSERT INTO dag_snapshot_commits (
    snapshot_id,
    commit_hash,
    commit_time,
    parent_count
)
SELECT
    s.snapshot_id,
    c.commit_hash,
    c.commit_time,
    count(p.commit_hash)::integer
FROM dag_snapshots s
         JOIN dag_source_commits c
              ON c.project_ref = s.project_ref
                  AND c.commit_time < s.cutoff_at
         LEFT JOIN dag_source_edges e
                   ON e.project_ref = c.project_ref
                       AND e.child_hash = c.commit_hash
         LEFT JOIN dag_source_commits p
                   ON p.project_ref = e.project_ref
                       AND p.commit_hash = e.parent_hash
                       AND p.commit_time < s.cutoff_at
GROUP BY
    s.snapshot_id,
    c.commit_hash,
    c.commit_time
ON CONFLICT (snapshot_id, commit_hash) DO NOTHING;


-- =========================================================
-- 10. Populate snapshot edges.
-- An edge is included only when both nodes are included.
-- =========================================================

INSERT INTO dag_snapshot_edges (
    snapshot_id,
    parent_hash,
    child_hash
)
SELECT
    s.snapshot_id,
    e.parent_hash,
    e.child_hash
FROM dag_snapshots s
         JOIN dag_source_edges e
              ON e.project_ref = s.project_ref
         JOIN dag_snapshot_commits parent
              ON parent.snapshot_id = s.snapshot_id
                  AND parent.commit_hash = e.parent_hash
         JOIN dag_snapshot_commits child
              ON child.snapshot_id = s.snapshot_id
                  AND child.commit_hash = e.child_hash
ON CONFLICT (snapshot_id, parent_hash, child_hash) DO NOTHING;


-- =========================================================
-- 11. Update snapshot counts
-- =========================================================

WITH node_counts AS (
    SELECT snapshot_id, count(*) AS total
    FROM dag_snapshot_commits
    GROUP BY snapshot_id
),
     edge_counts AS (
         SELECT snapshot_id, count(*) AS total
         FROM dag_snapshot_edges
         GROUP BY snapshot_id
     ),
     counts AS (
         SELECT
             s.snapshot_id,
             coalesce(n.total, 0) AS commit_count,
             coalesce(e.total, 0) AS edge_count
         FROM dag_snapshots s
                  LEFT JOIN node_counts n USING (snapshot_id)
                  LEFT JOIN edge_counts e USING (snapshot_id)
     )
UPDATE dag_snapshots s
SET
    commit_count = counts.commit_count,
    edge_count = counts.edge_count,
    generated_at = now()
FROM counts
WHERE counts.snapshot_id = s.snapshot_id;