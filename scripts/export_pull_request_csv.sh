#!/usr/bin/env bash
set -Eeuo pipefail

# PostgreSQL performs RFC-compatible CSV quoting, including embedded CR/LF.
# Usage: DATABASE_URL=postgresql://... ./scripts/export_pull_request_csv.sh [output-dir]

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_DIR="${1:-$ROOT_DIR/export}"
mkdir -p "$OUTPUT_DIR"
OUTPUT_DIR="$(cd "$OUTPUT_DIR" && pwd)"
: "${DATABASE_URL:?Set DATABASE_URL to the PostgreSQL connection URI}"
command -v psql >/dev/null || { echo "psql is required" >&2; exit 1; }
command -v python3 >/dev/null || { echo "python3 is required for CSV validation" >&2; exit 1; }

psql "$DATABASE_URL" -X -v ON_ERROR_STOP=1 \
  -c "\\copy (SELECT id, project_ref, pr_number, pr_title, pr_url, pr_state, created_at, merged_at, time_to_merge_seconds, review_count, comment_count, additions, deletions, changed_files FROM pull_requests ORDER BY project_ref, pr_number) TO '$OUTPUT_DIR/pull_requests.csv' WITH (FORMAT CSV, HEADER TRUE, ENCODING 'UTF8')" \
  -c "\\copy (SELECT pr.project_ref, pr.pr_number, c.commit_hash FROM commit_pull_request_links link JOIN commits c ON c.id = link.commit_id JOIN pull_requests pr ON pr.id = link.pull_request_id ORDER BY pr.project_ref, pr.pr_number, c.commit_hash) TO '$OUTPUT_DIR/commit_pull_requests.csv' WITH (FORMAT CSV, HEADER TRUE, ENCODING 'UTF8')"

python3 - "$OUTPUT_DIR" <<'PY'
import csv
import pathlib
import sys

directory = pathlib.Path(sys.argv[1])
expected = {"pull_requests.csv": 14, "commit_pull_requests.csv": 3}
for name, width in expected.items():
    with (directory / name).open("r", encoding="utf-8", newline="") as stream:
        reader = csv.reader(stream, strict=True)
        header = next(reader)
        if len(header) != width:
            raise SystemExit(f"{name}: expected {width} columns in header, found {len(header)}")
        count = 0
        for record_number, row in enumerate(reader, start=2):
            if len(row) != width:
                raise SystemExit(f"{name}: record {record_number} has {len(row)} fields; expected {width}")
            count += 1
    print(f"Validated {name}: {count} logical records, {width} columns")
PY
