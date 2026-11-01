BEGIN;

CREATE TABLE IF NOT EXISTS dag_snapshot_clustering_features (
                                                                snapshot_id bigint PRIMARY KEY
                                                                    REFERENCES dag_snapshots(snapshot_id) ON DELETE CASCADE,
                                                                project_ref text NOT NULL,
                                                                quarter_start date NOT NULL,
                                                                commit_count bigint NOT NULL,

                                                                branch_point_rate double precision NOT NULL,
                                                                merge_rate double precision NOT NULL,
                                                                root_rate double precision NOT NULL,
                                                                leaf_rate double precision NOT NULL,
                                                                avg_children_per_branch_point double precision NOT NULL,
                                                                avg_parents_per_merge_commit double precision NOT NULL,

                                                                cluster_eligible boolean NOT NULL,

                                                                z_branch_point_rate double precision,
                                                                z_merge_rate double precision,
                                                                z_root_rate double precision,
                                                                z_leaf_rate double precision,
                                                                z_avg_children_per_branch_point double precision,
                                                                z_avg_parents_per_merge_commit double precision,

                                                                generated_at timestamptz NOT NULL DEFAULT now(),
                                                                UNIQUE (project_ref, quarter_start)
);

CREATE INDEX IF NOT EXISTS idx_dag_clustering_project_quarter
    ON dag_snapshot_clustering_features(project_ref, quarter_start);

TRUNCATE TABLE dag_snapshot_clustering_features;

WITH child_counts AS (
    SELECT
        snapshot_id,
        parent_hash AS commit_hash,
        COUNT(*)::bigint AS child_count
    FROM dag_snapshot_edges
    GROUP BY snapshot_id, parent_hash
),
     node_values AS (
         SELECT
             c.snapshot_id,
             c.parent_count,
             COALESCE(cc.child_count, 0)::bigint AS child_count
         FROM dag_snapshot_commits c
                  LEFT JOIN child_counts cc
                            ON cc.snapshot_id = c.snapshot_id
                                AND cc.commit_hash = c.commit_hash
     ),
     degree_means AS (
         SELECT
             snapshot_id,
             AVG(child_count::double precision)
                 FILTER (WHERE child_count > 1)
            AS avg_children_per_branch_point,
             AVG(parent_count::double precision)
                 FILTER (WHERE parent_count > 1)
            AS avg_parents_per_merge_commit
         FROM node_values
         GROUP BY snapshot_id
     ),
     raw_features AS (
         SELECT
             f.snapshot_id,
             f.project_ref,
             f.quarter_start,
             f.commit_count,
             COALESCE(f.branch_point_rate, 0)::double precision
    AS branch_point_rate,
    COALESCE(f.merge_rate, 0)::double precision
    AS merge_rate,
    COALESCE(
    f.root_count::double precision / NULLIF(f.commit_count, 0), 0
    ) AS root_rate,
    COALESCE(
    f.leaf_count::double precision / NULLIF(f.commit_count, 0), 0
    ) AS leaf_rate,
    COALESCE(m.avg_children_per_branch_point, 0)::double precision
    AS avg_children_per_branch_point,
    COALESCE(m.avg_parents_per_merge_commit, 0)::double precision
    AS avg_parents_per_merge_commit,
    f.commit_count >= 1000 AS cluster_eligible
FROM dag_snapshot_features f
    LEFT JOIN degree_means m
ON m.snapshot_id = f.snapshot_id
    ),
    stats AS (
SELECT
    AVG(branch_point_rate) FILTER (WHERE cluster_eligible) AS m1,
    NULLIF(STDDEV_POP(branch_point_rate)
    FILTER (WHERE cluster_eligible), 0) AS s1,
    AVG(merge_rate) FILTER (WHERE cluster_eligible) AS m2,
    NULLIF(STDDEV_POP(merge_rate)
    FILTER (WHERE cluster_eligible), 0) AS s2,
    AVG(root_rate) FILTER (WHERE cluster_eligible) AS m3,
    NULLIF(STDDEV_POP(root_rate)
    FILTER (WHERE cluster_eligible), 0) AS s3,
    AVG(leaf_rate) FILTER (WHERE cluster_eligible) AS m4,
    NULLIF(STDDEV_POP(leaf_rate)
    FILTER (WHERE cluster_eligible), 0) AS s4,
    AVG(avg_children_per_branch_point)
    FILTER (WHERE cluster_eligible) AS m5,
    NULLIF(STDDEV_POP(avg_children_per_branch_point)
    FILTER (WHERE cluster_eligible), 0) AS s5,
    AVG(avg_parents_per_merge_commit)
    FILTER (WHERE cluster_eligible) AS m6,
    NULLIF(STDDEV_POP(avg_parents_per_merge_commit)
    FILTER (WHERE cluster_eligible), 0) AS s6
FROM raw_features
    )
INSERT INTO dag_snapshot_clustering_features (
    snapshot_id, project_ref, quarter_start, commit_count,
    branch_point_rate, merge_rate, root_rate, leaf_rate,
    avg_children_per_branch_point, avg_parents_per_merge_commit,
    cluster_eligible,
    z_branch_point_rate, z_merge_rate, z_root_rate, z_leaf_rate,
    z_avg_children_per_branch_point, z_avg_parents_per_merge_commit
)
SELECT
    r.snapshot_id, r.project_ref, r.quarter_start, r.commit_count,
    r.branch_point_rate, r.merge_rate, r.root_rate, r.leaf_rate,
    r.avg_children_per_branch_point, r.avg_parents_per_merge_commit,
    r.cluster_eligible,

    CASE WHEN r.cluster_eligible THEN
             COALESCE((r.branch_point_rate - s.m1) / NULLIF(s.s1, 0), 0)
        END,
    CASE WHEN r.cluster_eligible THEN
             COALESCE((r.merge_rate - s.m2) / NULLIF(s.s2, 0), 0)
        END,
    CASE WHEN r.cluster_eligible THEN
             COALESCE((r.root_rate - s.m3) / NULLIF(s.s3, 0), 0)
        END,
    CASE WHEN r.cluster_eligible THEN
             COALESCE((r.leaf_rate - s.m4) / NULLIF(s.s4, 0), 0)
        END,
    CASE WHEN r.cluster_eligible THEN
             COALESCE(
                     (r.avg_children_per_branch_point - s.m5) / NULLIF(s.s5, 0), 0
             )
        END,
    CASE WHEN r.cluster_eligible THEN
             COALESCE(
                     (r.avg_parents_per_merge_commit - s.m6) / NULLIF(s.s6, 0), 0
             )
        END
FROM raw_features r
         CROSS JOIN stats s;

COMMIT;