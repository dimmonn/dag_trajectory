-- =========================================================
-- Rebuild quarterly DAG advanced features
--
-- 1. Snapshot-local graph depth
-- 2. Direct-child time spread
-- 3. Second-parent timestamp difference (diagnostic)
-- 4. Pull-request cycle time
--
-- Uses existing DAG snapshot and GitHub metadata tables.
-- Does not modify original ingestion tables.
-- =========================================================

ROLLBACK;

BEGIN;


-- =========================================================
-- 1. Create the advanced feature table
-- =========================================================

CREATE TABLE IF NOT EXISTS public.dag_snapshot_advanced_features (
                                                                     snapshot_id bigint PRIMARY KEY
                                                                         REFERENCES public.dag_snapshots(snapshot_id) ON DELETE CASCADE,

                                                                     project_ref text NOT NULL,
                                                                     quarter_start date NOT NULL,
                                                                     commit_count bigint NOT NULL,

    -- Graph depth
                                                                     depth_observation_count bigint NOT NULL DEFAULT 0,
                                                                     depth_mean double precision,
                                                                     depth_p50 double precision,
                                                                     depth_p90 double precision,
                                                                     depth_p95 double precision,
                                                                     depth_max integer,
                                                                     depth_excluded_parent_edges bigint NOT NULL DEFAULT 0,
                                                                     depth_valid boolean NOT NULL DEFAULT false,
                                                                     depth_method text,

    -- Branching
                                                                     branch_point_count bigint NOT NULL DEFAULT 0,
                                                                     direct_child_time_spread_observation_count bigint NOT NULL DEFAULT 0,
                                                                     direct_child_time_spread_p50_seconds double precision,
                                                                     direct_child_time_spread_p90_seconds double precision,

    -- Merging
                                                                     merge_count bigint NOT NULL DEFAULT 0,

    -- Diagnostic timestamp measure
                                                                     second_parent_timestamp_lag_observation_count bigint NOT NULL DEFAULT 0,
                                                                     second_parent_timestamp_lag_p50_seconds double precision,
                                                                     second_parent_timestamp_lag_p90_seconds double precision,
                                                                     negative_second_parent_timestamp_lag_count bigint NOT NULL DEFAULT 0,

    -- Pull-request cycle time
                                                                     pr_cycle_time_observation_count bigint NOT NULL DEFAULT 0,
                                                                     pr_cycle_time_p50_seconds double precision,
                                                                     pr_cycle_time_p90_seconds double precision,
                                                                     pr_cycle_time_negative_count bigint NOT NULL DEFAULT 0,

                                                                     generated_at timestamptz NOT NULL DEFAULT now(),

                                                                     UNIQUE (project_ref, quarter_start)
);


CREATE INDEX IF NOT EXISTS idx_dag_advanced_features_project_quarter
    ON public.dag_snapshot_advanced_features(project_ref, quarter_start);


-- =========================================================
-- 2. Extract original graph-depth values from graph JSON
-- =========================================================

CREATE TEMP TABLE tmp_source_commit_depth
    ON COMMIT DROP
AS
SELECT
    pg.project_ref,
    n.node_json ->> 'hash' AS commit_hash,

    CASE
        WHEN COALESCE(
                     n.node_json -> 'metrics' ->> 'graphDepth', ''
             ) ~ '^[0-9]+$'
            THEN (n.node_json -> 'metrics' ->> 'graphDepth')::integer
        ELSE NULL
        END AS graph_depth

FROM public.project_graphs pg

         CROSS JOIN LATERAL jsonb_array_elements(
        CASE
            WHEN jsonb_typeof(pg.graph_json::jsonb -> 'commits') = 'array'
                THEN pg.graph_json::jsonb -> 'commits'
            ELSE '[]'::jsonb
            END
                            ) AS n(node_json)

WHERE pg.source_project_ref IS NULL
  AND n.node_json ->> 'hash' IS NOT NULL;


CREATE UNIQUE INDEX tmp_source_commit_depth_uidx
    ON tmp_source_commit_depth(project_ref, commit_hash);


-- =========================================================
-- 3. Identify parent edges excluded from quarterly snapshots
-- =========================================================

CREATE TEMP TABLE tmp_excluded_parent_counts
    ON COMMIT DROP
AS
SELECT
    child.snapshot_id,
    COUNT(*)::bigint AS excluded_parent_edges

FROM public.dag_snapshot_commits child

         JOIN public.dag_snapshots s
              ON s.snapshot_id = child.snapshot_id

         JOIN public.dag_source_edges e
              ON e.project_ref = s.project_ref
                  AND e.child_hash = child.commit_hash

         LEFT JOIN public.dag_snapshot_commits parent
                   ON parent.snapshot_id = child.snapshot_id
                       AND parent.commit_hash = e.parent_hash

WHERE parent.commit_hash IS NULL

GROUP BY child.snapshot_id;


CREATE UNIQUE INDEX tmp_excluded_parent_counts_uidx
    ON tmp_excluded_parent_counts(snapshot_id);


-- =========================================================
-- 4. Identify snapshots requiring depth recalculation
--
-- Recalculate when:
-- - a parent edge was excluded; or
-- - original graph depth is missing for any commit.
-- =========================================================

CREATE TEMP TABLE tmp_depth_recalc_snapshots
    ON COMMIT DROP
AS
WITH source_counts AS (
    SELECT
        c.snapshot_id,
        COUNT(d.graph_depth)::bigint AS available_depths

    FROM public.dag_snapshot_commits c

             JOIN public.dag_snapshots s
                  ON s.snapshot_id = c.snapshot_id

             LEFT JOIN tmp_source_commit_depth d
                       ON d.project_ref = s.project_ref
                           AND d.commit_hash = c.commit_hash

    GROUP BY c.snapshot_id
)

SELECT
    s.snapshot_id

FROM public.dag_snapshots s

         LEFT JOIN tmp_excluded_parent_counts e
                   ON e.snapshot_id = s.snapshot_id

         LEFT JOIN source_counts d
                   ON d.snapshot_id = s.snapshot_id

WHERE COALESCE(e.excluded_parent_edges, 0) > 0
   OR COALESCE(d.available_depths, 0) <> s.commit_count;


CREATE UNIQUE INDEX tmp_depth_recalc_snapshots_uidx
    ON tmp_depth_recalc_snapshots(snapshot_id);


-- =========================================================
-- 5. Prepare snapshot-local depth values
--
-- Unaffected snapshots reuse original graph depth.
-- =========================================================

CREATE TEMP TABLE tmp_snapshot_node_depth (
                                              snapshot_id bigint NOT NULL,
                                              commit_hash text NOT NULL,
                                              depth integer NOT NULL,
                                              PRIMARY KEY (snapshot_id, commit_hash)
)
    ON COMMIT DROP;


INSERT INTO tmp_snapshot_node_depth (
    snapshot_id,
    commit_hash,
    depth
)
SELECT
    c.snapshot_id,
    c.commit_hash,
    d.graph_depth

FROM public.dag_snapshot_commits c

         JOIN public.dag_snapshots s
              ON s.snapshot_id = c.snapshot_id

         JOIN tmp_source_commit_depth d
              ON d.project_ref = s.project_ref
                  AND d.commit_hash = c.commit_hash

WHERE NOT EXISTS (
    SELECT 1
    FROM tmp_depth_recalc_snapshots r
    WHERE r.snapshot_id = c.snapshot_id
)
  AND d.graph_depth IS NOT NULL;


-- =========================================================
-- 6. Recalculate depth using an incremental topological queue
--
-- Root depth = 0.
-- Child depth = maximum parent depth + 1.
--
-- Each node is processed when all its snapshot parents
-- have been processed.
--
-- This avoids repeatedly scanning all unresolved nodes.
-- =========================================================

CREATE TEMP TABLE tmp_recalc_nodes (
                                       snapshot_id bigint NOT NULL,
                                       commit_hash text NOT NULL,
                                       pending_parents bigint NOT NULL,
                                       max_parent_depth bigint NOT NULL DEFAULT -1,
                                       depth bigint,
                                       PRIMARY KEY (snapshot_id, commit_hash)
)
    ON COMMIT DROP;


INSERT INTO tmp_recalc_nodes (
    snapshot_id,
    commit_hash,
    pending_parents
)
SELECT
    c.snapshot_id,
    c.commit_hash,
    COUNT(e.parent_hash)::bigint

FROM public.dag_snapshot_commits c

         JOIN tmp_depth_recalc_snapshots r
              ON r.snapshot_id = c.snapshot_id

         LEFT JOIN public.dag_snapshot_edges e
                   ON e.snapshot_id = c.snapshot_id
                       AND e.child_hash = c.commit_hash

GROUP BY
    c.snapshot_id,
    c.commit_hash;


ANALYZE tmp_recalc_nodes;


-- Current queue: nodes ready to be processed.

CREATE TEMP TABLE tmp_recalc_ready (
                                       snapshot_id bigint NOT NULL,
                                       commit_hash text NOT NULL,
                                       PRIMARY KEY (snapshot_id, commit_hash)
)
    ON COMMIT DROP;


-- Next queue: children that become ready in this iteration.

CREATE TEMP TABLE tmp_recalc_next_ready (
                                            snapshot_id bigint NOT NULL,
                                            commit_hash text NOT NULL,
                                            PRIMARY KEY (snapshot_id, commit_hash)
)
    ON COMMIT DROP;


-- Initially, roots have no parents inside their snapshot.

INSERT INTO tmp_recalc_ready (
    snapshot_id,
    commit_hash
)
SELECT
    snapshot_id,
    commit_hash
FROM tmp_recalc_nodes
WHERE pending_parents = 0;


-- Process the graph level by level.

DO $depth_recalc$
    DECLARE
        v_ready_count bigint;
        v_unresolved bigint;
    BEGIN

        SELECT COUNT(*)
        INTO v_ready_count
        FROM tmp_recalc_ready;


        WHILE v_ready_count > 0 LOOP

            -- Assign depth to the currently ready nodes.
            -- Roots receive depth zero.

                UPDATE tmp_recalc_nodes n
                SET depth = n.max_parent_depth + 1

                FROM tmp_recalc_ready r

                WHERE n.snapshot_id = r.snapshot_id
                  AND n.commit_hash = r.commit_hash
                  AND n.depth IS NULL;


                -- Clear the next queue.

                TRUNCATE TABLE tmp_recalc_next_ready;


                -- Send the processed nodes' depths to their children.
                -- A child becomes ready when all its parents are processed.

                WITH child_contributions AS MATERIALIZED (

                    SELECT
                        e.snapshot_id,
                        e.child_hash,
                        MAX(p.depth) AS max_parent_depth,
                        COUNT(*)::bigint AS processed_parent_count

                    FROM tmp_recalc_ready r

                             JOIN tmp_recalc_nodes p
                                  ON p.snapshot_id = r.snapshot_id
                                      AND p.commit_hash = r.commit_hash

                             JOIN public.dag_snapshot_edges e
                                  ON e.snapshot_id = r.snapshot_id
                                      AND e.parent_hash = r.commit_hash

                    GROUP BY
                        e.snapshot_id,
                        e.child_hash

                ),

                     updated_children AS (

                         UPDATE tmp_recalc_nodes n

                             SET
                                 max_parent_depth = GREATEST(
                                         n.max_parent_depth,
                                         x.max_parent_depth
                                                    ),

                                 pending_parents =
                                         n.pending_parents - x.processed_parent_count

                             FROM child_contributions x

                             WHERE n.snapshot_id = x.snapshot_id
                                 AND n.commit_hash = x.child_hash
                                 AND n.depth IS NULL

                             RETURNING
                                 n.snapshot_id,
                                 n.commit_hash,
                                 n.pending_parents,
                                 n.depth

                     )

                INSERT INTO tmp_recalc_next_ready (
                    snapshot_id,
                    commit_hash
                )

                SELECT
                    snapshot_id,
                    commit_hash

                FROM updated_children

                WHERE depth IS NULL
                  AND pending_parents = 0;


                -- Move the next queue into the current queue.

                TRUNCATE TABLE tmp_recalc_ready;


                INSERT INTO tmp_recalc_ready (
                    snapshot_id,
                    commit_hash
                )

                SELECT
                    snapshot_id,
                    commit_hash

                FROM tmp_recalc_next_ready;


                GET DIAGNOSTICS v_ready_count = ROW_COUNT;

            END LOOP;


        -- If any node remains unresolved, the graph contains
        -- a cycle or an inconsistent parent-edge structure.

        SELECT COUNT(*)
        INTO v_unresolved

        FROM tmp_recalc_nodes

        WHERE depth IS NULL;


        IF v_unresolved > 0 THEN

            RAISE EXCEPTION
                'Depth calculation failed: % commits remain unresolved',
                v_unresolved;

        END IF;

    END
$depth_recalc$;


-- Save recalculated depths.

CREATE TEMP TABLE tmp_recalculated_depth
    ON COMMIT DROP
AS
SELECT
    snapshot_id,
    commit_hash,
    depth::integer AS depth

FROM tmp_recalc_nodes

WHERE depth IS NOT NULL;


CREATE UNIQUE INDEX tmp_recalculated_depth_uidx
    ON tmp_recalculated_depth(snapshot_id, commit_hash);


INSERT INTO tmp_snapshot_node_depth (
    snapshot_id,
    commit_hash,
    depth
)
SELECT
    snapshot_id,
    commit_hash,
    depth

FROM tmp_recalculated_depth;


-- =========================================================
-- 7. Calculate depth distributions
-- =========================================================

CREATE TEMP TABLE tmp_depth_stats
    ON COMMIT DROP
AS
SELECT
    snapshot_id,

    COUNT(*)::bigint AS observation_count,

    AVG(depth::double precision) AS depth_mean,

    percentile_cont(0.50) WITHIN GROUP (
        ORDER BY depth::double precision
        ) AS depth_p50,

    percentile_cont(0.90) WITHIN GROUP (
        ORDER BY depth::double precision
        ) AS depth_p90,

    percentile_cont(0.95) WITHIN GROUP (
        ORDER BY depth::double precision
        ) AS depth_p95,

    MAX(depth)::integer AS depth_max

FROM tmp_snapshot_node_depth

GROUP BY snapshot_id;


-- =========================================================
-- 8. Calculate direct-child time spread
--
-- Difference between earliest and latest direct child
-- commit timestamps of each branching commit.
--
-- This is NOT branch lifetime.
-- =========================================================

CREATE TEMP TABLE tmp_branch_stats
    ON COMMIT DROP
AS
WITH branch_points AS (

    SELECT
        e.snapshot_id,
        e.parent_hash,

        COUNT(*)::bigint AS child_count,

        MIN(child.commit_time) AS first_child_time,
        MAX(child.commit_time) AS last_child_time

    FROM public.dag_snapshot_edges e

             JOIN public.dag_snapshot_commits child
                  ON child.snapshot_id = e.snapshot_id
                      AND child.commit_hash = e.child_hash

    GROUP BY
        e.snapshot_id,
        e.parent_hash

    HAVING COUNT(*) > 1

)

SELECT
    snapshot_id,

    COUNT(*)::bigint AS branch_point_count,

    COUNT(*)::bigint AS observation_count,

    percentile_cont(0.50) WITHIN GROUP (
        ORDER BY EXTRACT(
                EPOCH FROM (last_child_time - first_child_time)
                 )::double precision
        ) AS spread_p50,

    percentile_cont(0.90) WITHIN GROUP (
        ORDER BY EXTRACT(
                EPOCH FROM (last_child_time - first_child_time)
                 )::double precision
        ) AS spread_p90

FROM branch_points

GROUP BY snapshot_id;


-- =========================================================
-- 9. Count merge commits
-- =========================================================

CREATE TEMP TABLE tmp_merge_counts
    ON COMMIT DROP
AS
SELECT
    snapshot_id,

    COUNT(*) FILTER (
        WHERE parent_count > 1
        )::bigint AS merge_count

FROM public.dag_snapshot_commits

GROUP BY snapshot_id;


-- =========================================================
-- 10. Calculate second-parent timestamp differences
--
-- Diagnostic only:
-- merge commit timestamp - second-parent timestamp.
--
-- This is NOT PR cycle time or branch lifetime.
-- =========================================================

CREATE TEMP TABLE tmp_second_parent_lags
    ON COMMIT DROP
AS
SELECT
    merge_commit.snapshot_id,

    EXTRACT(
            EPOCH FROM (
        merge_commit.commit_time - second_parent.commit_time
        )
    )::double precision AS lag_seconds

FROM public.dag_snapshot_commits merge_commit

         JOIN public.dag_snapshots s
              ON s.snapshot_id = merge_commit.snapshot_id

         JOIN public.dag_source_commits src
              ON src.project_ref = s.project_ref
                  AND src.commit_hash = merge_commit.commit_hash

         CROSS JOIN LATERAL jsonb_array_elements_text(
        src.parents
                            ) WITH ORDINALITY AS p(parent_hash, parent_position)

         JOIN public.dag_snapshot_commits second_parent
              ON second_parent.snapshot_id = merge_commit.snapshot_id
                  AND second_parent.commit_hash = p.parent_hash

WHERE merge_commit.parent_count > 1
  AND p.parent_position = 2;


CREATE TEMP TABLE tmp_second_parent_stats
    ON COMMIT DROP
AS
SELECT
    snapshot_id,

    COUNT(*)::bigint AS observation_count,

    COUNT(*) FILTER (
        WHERE lag_seconds < 0
        )::bigint AS negative_count,

    percentile_cont(0.50) WITHIN GROUP (
        ORDER BY lag_seconds
        ) FILTER (
        WHERE lag_seconds >= 0
        ) AS lag_p50,

    percentile_cont(0.90) WITHIN GROUP (
        ORDER BY lag_seconds
        ) FILTER (
        WHERE lag_seconds >= 0
        ) AS lag_p90

FROM tmp_second_parent_lags

GROUP BY snapshot_id;


-- =========================================================
-- 11. Calculate PR cycle time
--
-- PR cycle time = merged_at - created_at.
--
-- Include PRs merged during the snapshot quarter.
-- Count each PR only once per snapshot.
--
-- Negative values are retained for anomaly counting.
-- =========================================================

CREATE TEMP TABLE tmp_pr_cycle_times
    ON COMMIT DROP
AS
SELECT DISTINCT
    s.snapshot_id,
    pr.id AS pull_request_id,

    (pr.merged_at - pr.created_at)::double precision
          AS cycle_seconds

FROM public.dag_snapshots s

         JOIN public.dag_snapshot_commits sc
              ON sc.snapshot_id = s.snapshot_id

         JOIN public.commits c
              ON c.project_ref = s.project_ref
                  AND c.commit_hash = sc.commit_hash

         JOIN public.commit_pull_request_links link
              ON link.commit_id = c.id

         JOIN public.pull_requests pr
              ON pr.id = link.pull_request_id
                  AND pr.project_ref = s.project_ref

WHERE pr.created_at IS NOT NULL
  AND pr.merged_at IS NOT NULL

  AND to_timestamp(pr.merged_at) >=
      (s.quarter_start::timestamp AT TIME ZONE 'UTC')

  AND to_timestamp(pr.merged_at) < s.cutoff_at;


CREATE TEMP TABLE tmp_pr_cycle_stats
    ON COMMIT DROP
AS
SELECT
    snapshot_id,

    COUNT(*) FILTER (
        WHERE cycle_seconds >= 0
        )::bigint AS observation_count,

    COUNT(*) FILTER (
        WHERE cycle_seconds < 0
        )::bigint AS negative_count,

    percentile_cont(0.50) WITHIN GROUP (
        ORDER BY cycle_seconds
        ) FILTER (
        WHERE cycle_seconds >= 0
        ) AS cycle_p50,

    percentile_cont(0.90) WITHIN GROUP (
        ORDER BY cycle_seconds
        ) FILTER (
        WHERE cycle_seconds >= 0
        ) AS cycle_p90

FROM tmp_pr_cycle_times

GROUP BY snapshot_id;


-- =========================================================
-- 12. Rebuild the advanced feature table
-- =========================================================

TRUNCATE TABLE public.dag_snapshot_advanced_features;


INSERT INTO public.dag_snapshot_advanced_features (

    snapshot_id,
    project_ref,
    quarter_start,
    commit_count,

    depth_observation_count,
    depth_mean,
    depth_p50,
    depth_p90,
    depth_p95,
    depth_max,
    depth_excluded_parent_edges,
    depth_valid,
    depth_method,

    branch_point_count,
    direct_child_time_spread_observation_count,
    direct_child_time_spread_p50_seconds,
    direct_child_time_spread_p90_seconds,

    merge_count,

    second_parent_timestamp_lag_observation_count,
    second_parent_timestamp_lag_p50_seconds,
    second_parent_timestamp_lag_p90_seconds,
    negative_second_parent_timestamp_lag_count,

    pr_cycle_time_observation_count,
    pr_cycle_time_p50_seconds,
    pr_cycle_time_p90_seconds,
    pr_cycle_time_negative_count,

    generated_at
)

SELECT
    s.snapshot_id,
    s.project_ref,
    s.quarter_start,
    s.commit_count,

    COALESCE(d.observation_count, 0),
    d.depth_mean,
    d.depth_p50,
    d.depth_p90,
    d.depth_p95,
    d.depth_max,

    COALESCE(e.excluded_parent_edges, 0),

    (
        COALESCE(d.observation_count, 0) = s.commit_count
            AND COALESCE(d.depth_max, 0) < s.commit_count
        ),

    CASE
        WHEN r.snapshot_id IS NOT NULL
            THEN 'snapshot_recomputed'
        ELSE 'original_graph_metric'
        END,

    COALESCE(b.branch_point_count, 0),
    COALESCE(b.observation_count, 0),
    b.spread_p50,
    b.spread_p90,

    COALESCE(m.merge_count, 0),

    COALESCE(sl.observation_count, 0),
    sl.lag_p50,
    sl.lag_p90,
    COALESCE(sl.negative_count, 0),

    COALESCE(pr.observation_count, 0),
    pr.cycle_p50,
    pr.cycle_p90,
    COALESCE(pr.negative_count, 0),

    now()

FROM public.dag_snapshots s

         LEFT JOIN tmp_depth_stats d
                   ON d.snapshot_id = s.snapshot_id

         LEFT JOIN tmp_excluded_parent_counts e
                   ON e.snapshot_id = s.snapshot_id

         LEFT JOIN tmp_depth_recalc_snapshots r
                   ON r.snapshot_id = s.snapshot_id

         LEFT JOIN tmp_branch_stats b
                   ON b.snapshot_id = s.snapshot_id

         LEFT JOIN tmp_merge_counts m
                   ON m.snapshot_id = s.snapshot_id

         LEFT JOIN tmp_second_parent_stats sl
                   ON sl.snapshot_id = s.snapshot_id

         LEFT JOIN tmp_pr_cycle_stats pr
                   ON pr.snapshot_id = s.snapshot_id;


COMMIT;


-- =========================================================
-- 13. Validate the result
-- =========================================================

SELECT
    COUNT(*) AS total_snapshots,

    COUNT(DISTINCT project_ref) AS repositories,

    COUNT(*) FILTER (
        WHERE depth_valid
        ) AS valid_depth_snapshots,

    COUNT(*) FILTER (
        WHERE NOT depth_valid
        ) AS invalid_depth_snapshots,

    COUNT(*) FILTER (
        WHERE depth_method = 'snapshot_recomputed'
        ) AS recalculated_snapshots,

    SUM(depth_observation_count) AS depth_observations,

    SUM(depth_excluded_parent_edges) AS excluded_parent_edges,

    SUM(pr_cycle_time_observation_count) AS pr_cycle_observations

FROM public.dag_snapshot_advanced_features;


-- Show any snapshots with invalid or incomplete depth.

SELECT
    project_ref,
    quarter_start,
    commit_count,
    depth_observation_count,
    depth_max,
    depth_valid,
    depth_method

FROM public.dag_snapshot_advanced_features

WHERE NOT depth_valid
   OR depth_observation_count <> commit_count

ORDER BY
    project_ref,
    quarter_start;