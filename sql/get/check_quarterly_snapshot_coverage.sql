SELECT
    s.project_ref,
    pg.graph_json::jsonb ->> 'owner' AS owner,
    pg.graph_json::jsonb ->> 'name' AS repo,
    COUNT(*) AS quarters,
    MIN(s.quarter_start) AS first_quarter,
    MAX(s.quarter_start) AS last_quarter,
    MIN(s.commit_count) AS min_commits,
    MAX(s.commit_count) AS max_commits
FROM dag_snapshots s
         LEFT JOIN project_graphs pg
                   ON pg.project_ref = s.project_ref
GROUP BY s.project_ref, owner, repo
ORDER BY repo;