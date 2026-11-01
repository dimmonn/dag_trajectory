SELECT 'source_commits' AS table_name, COUNT(*) AS rows
FROM dag_source_commits
UNION ALL
SELECT 'source_edges', COUNT(*) FROM dag_source_edges
UNION ALL
SELECT 'snapshots', COUNT(*) FROM dag_snapshots
UNION ALL
SELECT 'snapshot_commits', COUNT(*) FROM dag_snapshot_commits
UNION ALL
SELECT 'snapshot_edges', COUNT(*) FROM dag_snapshot_edges;