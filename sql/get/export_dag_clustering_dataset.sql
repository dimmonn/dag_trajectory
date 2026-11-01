SELECT
    snapshot_id,
    project_ref,
    quarter_start,
    commit_count,
    branch_point_rate,
    merge_rate,
    root_rate,
    leaf_rate,
    avg_children_per_branch_point
FROM dag_snapshot_clustering_features
WHERE cluster_eligible
ORDER BY project_ref, quarter_start;