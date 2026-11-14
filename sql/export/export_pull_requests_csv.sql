-- Export pull requests and commit-to-pull-request links as standards-compliant CSV.
--
-- Run with psql from the repository root:
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f sql/export/export_pull_requests_csv.sql
--
-- PostgreSQL's CSV writer quotes fields containing commas, quotes, CR or LF.
-- This preserves multi-line PR titles/bodies as one logical CSV record.

\copy (SELECT id, project_ref, pr_number, title, url, pr_state, created_at, merged_at, is_merged, additions, deletions, changed_files FROM pull_requests ORDER BY project_ref, pr_number) TO 'pull_requests.csv' WITH (FORMAT CSV, HEADER TRUE, ENCODING 'UTF8', FORCE_QUOTE (title, url, pr_state))

\copy (SELECT id, project_ref, commit_hash, pull_request_id FROM commit_pull_request_links ORDER BY project_ref, pull_request_id, commit_hash) TO 'commit_pull_requests.csv' WITH (FORMAT CSV, HEADER TRUE, ENCODING 'UTF8')
