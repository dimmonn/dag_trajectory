# CSV export

Use `psql` and PostgreSQL's CSV writer for exports containing user-supplied GitHub text fields.

Run:

```bash
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f sql/export/export_pull_requests_csv.sql
```

The script exports `pull_requests.csv` and `commit_pull_requests.csv`. PostgreSQL's CSV format quotes embedded commas, double quotes, carriage returns, and line feeds, so a pull-request title/body containing a newline remains one logical CSV record. Do not export these query results as plain text with a comma delimiter or split output by physical line.
