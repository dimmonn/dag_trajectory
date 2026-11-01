SELECT
    COUNT(*) AS feature_rows,
    COUNT(DISTINCT project_ref) AS repositories,
    MIN(quarter_start) AS first_quarter,
    MAX(quarter_start) AS last_quarter
FROM dag_snapshot_features;