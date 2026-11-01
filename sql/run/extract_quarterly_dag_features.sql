-- PostgreSQL script: rebuild quarterly DAG structural features atomically.
-- If any statement fails inside the transaction, roll back rather than commit.

BEGIN;

-- Create the derived feature table.
CREATE TABLE IF NOT EXISTS dag_snapshot_features (
                                                     snapshot_id                 bigint PRIMARY KEY
                                                         REFERENCES dag_snapshots(snapshot_id) ON DELETE CASCADE,
                                                     project_ref                 text NOT NULL,
                                                     quarter_start               date NOT NULL,
                                                     cutoff_at                   timestamptz NOT NULL,

                                                     commit_count                bigint NOT NULL,
                                                     edge_count                  bigint NOT NULL,
                                                     root_count                  bigint NOT NULL,
                                                     leaf_count                  bigint NOT NULL,
                                                     branch_point_count          bigint NOT NULL,
                                                     merge_commit_count          bigint NOT NULL,

                                                     avg_parent_count            numeric,
                                                     max_parent_count            integer,
                                                     avg_child_count             numeric,
                                                     max_child_count             bigint,

                                                     branch_point_rate           numeric,
                                                     merge_rate                  numeric,
                                                     edges_per_commit            numeric,
                                                     roots_per_1000_commits      numeric,

                                                     quarter_new_commit_count    bigint NOT NULL,
                                                     quarter_new_edge_count      bigint NOT NULL,
                                                     new_commits_share           numeric,
                                                     new_edges_per_new_commit    numeric,
                                                     commit_growth_rate          numeric,
                                                     edge_growth_rate            numeric,

                                                     generated_at                timestamptz NOT NULL DEFAULT now(),
                                                     UNIQUE (project_ref, quarter_start)
);

CREATE INDEX IF NOT EXISTS idx_dag_snapshot_features_project_quarter
    ON dag_snapshot_features(project_ref, quarter_start);

-- Clear only the derived feature table.
-- The existing DAG snapshot, commit, and edge tables are untouched.
TRUNCATE TABLE dag_snapshot_features;

-- Calculate node degrees and quarterly commit activity.
WITH child_degrees AS (
    SELECT
        snapshot_id,
        parent_hash AS commit_hash,
        COUNT(*)::bigint AS child_count
    FROM dag_snapshot_edges
    GROUP BY snapshot_id, parent_hash
),
     node_rows AS (
         SELECT
             s.snapshot_id,
             s.project_ref,
             s.quarter_start,
             s.cutoff_at,
             c.commit_hash,
             c.commit_time,
             c.parent_count,
             COALESCE(cd.child_count, 0)::bigint AS child_count,
             (
                 c.commit_time >=
                 (s.quarter_start::timestamp AT TIME ZONE 'UTC')
                     AND c.commit_time < s.cutoff_at
                 ) AS is_new_this_quarter
         FROM dag_snapshots s
                  JOIN dag_snapshot_commits c
                       ON c.snapshot_id = s.snapshot_id
                  LEFT JOIN child_degrees cd
                            ON cd.snapshot_id = c.snapshot_id
                                AND cd.commit_hash = c.commit_hash
     ),
     node_aggregates AS (
         SELECT
             snapshot_id,
             COUNT(*)::bigint AS commit_count,
             COUNT(*) FILTER (WHERE parent_count = 0)::bigint
                              AS root_count,
             COUNT(*) FILTER (WHERE child_count = 0)::bigint
                              AS leaf_count,
             COUNT(*) FILTER (WHERE child_count > 1)::bigint
                              AS branch_point_count,
             COUNT(*) FILTER (WHERE parent_count > 1)::bigint
                              AS merge_commit_count,
             AVG(parent_count::numeric) AS avg_parent_count,
             MAX(parent_count)::integer AS max_parent_count,
             AVG(child_count::numeric) AS avg_child_count,
             MAX(child_count)::bigint AS max_child_count,
             COUNT(*) FILTER (WHERE is_new_this_quarter)::bigint
                 AS quarter_new_commit_count
         FROM node_rows
         GROUP BY snapshot_id
     ),
     edge_aggregates AS (
         SELECT
             s.snapshot_id,
             COUNT(e.child_hash)::bigint AS edge_count
         FROM dag_snapshots s
                  LEFT JOIN dag_snapshot_edges e
                            ON e.snapshot_id = s.snapshot_id
         GROUP BY s.snapshot_id
     ),
     combined AS (
         SELECT
             s.snapshot_id,
             s.project_ref,
             s.quarter_start,
             s.cutoff_at,
             COALESCE(n.commit_count, 0)::bigint AS commit_count,
             COALESCE(e.edge_count, 0)::bigint AS edge_count,
             COALESCE(n.root_count, 0)::bigint AS root_count,
             COALESCE(n.leaf_count, 0)::bigint AS leaf_count,
             COALESCE(n.branch_point_count, 0)::bigint
                                                 AS branch_point_count,
             COALESCE(n.merge_commit_count, 0)::bigint
                                                 AS merge_commit_count,
             n.avg_parent_count,
             n.max_parent_count,
             n.avg_child_count,
             n.max_child_count,
             COALESCE(n.quarter_new_commit_count, 0)::bigint
                                                 AS quarter_new_commit_count
         FROM dag_snapshots s
                  LEFT JOIN node_aggregates n USING (snapshot_id)
                  LEFT JOIN edge_aggregates e USING (snapshot_id)
     ),
     with_previous AS (
         SELECT
             c.*,
             LAG(quarter_new_commit_count) OVER (
                 PARTITION BY project_ref
                 ORDER BY quarter_start
                 ) AS previous_quarter_new_commits,
             LAG(edge_count) OVER (
                 PARTITION BY project_ref
                 ORDER BY quarter_start
                 ) AS previous_edge_count
         FROM combined c
     ),
     with_edge_deltas AS (
         SELECT
             wp.*,
             (
                 edge_count - COALESCE(previous_edge_count, 0)
                 )::bigint AS quarter_new_edge_count
         FROM with_previous wp
     ),
     with_both_lags AS (
         SELECT
             wd.*,
             LAG(quarter_new_edge_count) OVER (
                 PARTITION BY project_ref
                 ORDER BY quarter_start
                 ) AS previous_quarter_new_edges
         FROM with_edge_deltas wd
     )
INSERT INTO dag_snapshot_features (
    snapshot_id,
    project_ref,
    quarter_start,
    cutoff_at,
    commit_count,
    edge_count,
    root_count,
    leaf_count,
    branch_point_count,
    merge_commit_count,
    avg_parent_count,
    max_parent_count,
    avg_child_count,
    max_child_count,
    branch_point_rate,
    merge_rate,
    edges_per_commit,
    roots_per_1000_commits,
    quarter_new_commit_count,
    quarter_new_edge_count,
    new_commits_share,
    new_edges_per_new_commit,
    commit_growth_rate,
    edge_growth_rate,
    generated_at
)
SELECT
    snapshot_id,
    project_ref,
    quarter_start,
    cutoff_at,
    commit_count,
    edge_count,
    root_count,
    leaf_count,
    branch_point_count,
    merge_commit_count,
    avg_parent_count,
    max_parent_count,
    avg_child_count,
    max_child_count,
    branch_point_count::numeric / NULLIF(commit_count, 0),
    merge_commit_count::numeric / NULLIF(commit_count, 0),
    edge_count::numeric / NULLIF(commit_count, 0),
    root_count::numeric * 1000 / NULLIF(commit_count, 0),
    quarter_new_commit_count,
    quarter_new_edge_count,
    quarter_new_commit_count::numeric / NULLIF(commit_count, 0),
    quarter_new_edge_count::numeric
        / NULLIF(quarter_new_commit_count, 0),
    CASE
        WHEN previous_quarter_new_commits > 0
            THEN quarter_new_commit_count::numeric
                     / previous_quarter_new_commits - 1
        ELSE NULL
        END,
    CASE
        WHEN previous_quarter_new_edges > 0
            THEN quarter_new_edge_count::numeric
                     / previous_quarter_new_edges - 1
        ELSE NULL
        END,
    now()
FROM with_both_lags;

-- Review the extracted feature rows.
SELECT
    project_ref,
    quarter_start,
    commit_count,
    edge_count,
    branch_point_rate,
    merge_rate,
    avg_parent_count,
    avg_child_count,
    max_parent_count,
    max_child_count,
    quarter_new_commit_count,
    quarter_new_edge_count,
    commit_growth_rate,
    edge_growth_rate
FROM dag_snapshot_features
ORDER BY project_ref, quarter_start;

COMMIT;