-- Export pull requests and commit-to-pull-request links as standards-compliant CSV.
--
-- Run with psql from the repository root:
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f sql/export/export_pull_requests_csv.sql
--
-- PostgreSQL CSV output quotes fields containing commas, quotes, CR or LF.
-- A multi-line PR title therefore remains one logical CSV record.

\\copy (SELECT id, project_ref, pr_number, pr_title, pr_url, pr_state, created_at, merged_at, time_to_merge_seconds, review_count, comment_count, additions, deletions, changed_files FROM pull_requests ORDER BY project_ref, pr_number) TO 'pull_requests.csv' WITH (FORMAT CSV, HEADER TRUE, ENCODING 'UTF8')

\\copy (SELECT pr.project_ref, pr.pr_number, c.commit_hash FROM commit_pull_request_links link JOIN commits c ON c.id = link.commit_id JOIN pull_requests pr ON pr.id = link.pull_request_id ORDER BY pr.project_ref, pr.pr_number, c.commit_hash) TO 'commit_pull_requests.csv' WITH (FORMAT CSV, HEADER TRUE, ENCODING 'UTF8')
