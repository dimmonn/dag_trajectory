#!/usr/bin/env bash
set -Eeuo pipefail

# One-command ingestion runner for dimmonn/dag_trajectory.
# Usage:
#   GITHUB_TOKEN=... ./scripts/ingest.sh scikit-learn/scikit-learn psf/requests
#
# PostgreSQL must be running and match SPRING_DATASOURCE_* below.
# Defaults match src/main/resources/application.properties.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

: "${GITHUB_TOKEN:?Set GITHUB_TOKEN before running this script}"
PROJECTS=("$@")
if (("${#PROJECTS[@]}" == 0)); then
  PROJECTS=(
    "scikit-learn/scikit-learn"
    "mockito/mockito"
    "psf/requests"
    "microsoft/TypeScript"
  )
fi

export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://localhost:5432/dag_trajectory}"
export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-dag}"
export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-dag}"
export GITHUB_TOKEN
API_BASE="${INGEST_API_BASE:-http://localhost:8080}"
LOG_DIR="${INGEST_LOG_DIR:-$ROOT_DIR/logs}"
mkdir -p "$LOG_DIR"
APP_LOG="$LOG_DIR/ingestion-$(date +%Y%m%d-%H%M%S).log"
APP_PID=""

cleanup() {
  if [[ -n "$APP_PID" ]] && kill -0 "$APP_PID" 2>/dev/null; then
    kill "$APP_PID" 2>/dev/null || true
    wait "$APP_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

command -v java >/dev/null || { echo "Java is required." >&2; exit 1; }
command -v curl >/dev/null || { echo "curl is required." >&2; exit 1; }
command -v python3 >/dev/null || { echo "python3 is required to build the JSON request." >&2; exit 1; }
[[ -x ./gradlew ]] || { echo "Run this script from a checkout containing gradlew." >&2; exit 1; }

echo "Checking PostgreSQL..."
python3 - <<'PY'
import os, socket, sys
from urllib.parse import urlparse
u = urlparse(os.environ["SPRING_DATASOURCE_URL"].replace("jdbc:", "", 1))
host, port = u.hostname or "localhost", u.port or 5432
try:
    with socket.create_connection((host, port), timeout=5):
        pass
except OSError as e:
    sys.exit(f"Cannot connect to PostgreSQL at {host}:{port}: {e}")
PY

echo "Running tests..."
./gradlew test --no-daemon

echo "Building ingestion request..."
REQUEST_JSON="$(PROJECTS_LIST="$(printf '%s\n' "${PROJECTS[@]}")" python3 - <<'PY'
import json, os
projects = []
for item in os.environ["PROJECTS_LIST"].splitlines():
    if not item.strip():
        continue
    parts = item.strip().split("/", 1)
    if len(parts) != 2 or not all(parts):
        raise SystemExit(f"Invalid repository '{item}'; expected owner/name")
    projects.append({"owner": parts[0], "name": parts[1]})
print(json.dumps(projects))
PY
)"

echo "Starting application; log: $APP_LOG"
./gradlew bootRun --no-daemon >"$APP_LOG" 2>&1 &
APP_PID=$!

echo "Waiting for application health..."
ready=0
for _ in $(seq 1 90); do
  if ! kill -0 "$APP_PID" 2>/dev/null; then
    echo "Application exited before becoming ready. Log follows:" >&2
    tail -n 120 "$APP_LOG" >&2
    exit 1
  fi
  if curl --silent --fail "$API_BASE/actuator/health" >/dev/null; then
    ready=1
    break
  fi
  sleep 2
done
if [[ "$ready" != 1 ]]; then
  echo "Application did not become healthy within 180 seconds. Log follows:" >&2
  tail -n 120 "$APP_LOG" >&2
  exit 1
fi

echo "Submitting ingestion for: ${PROJECTS[*]}"
HTTP_CODE="$(curl --silent --show-error --output "$LOG_DIR/ingestion-response.json" --write-out '%{http_code}' \
  --request POST "$API_BASE/github-projects" \
  --header 'Content-Type: application/json' \
  --data "$REQUEST_JSON")"

cat "$LOG_DIR/ingestion-response.json"
echo
if [[ "$HTTP_CODE" -lt 200 || "$HTTP_CODE" -ge 300 ]]; then
  echo "Ingestion request failed (HTTP $HTTP_CODE). Application log: $APP_LOG" >&2
  exit 1
fi

echo "Ingestion request completed (HTTP $HTTP_CODE)."
echo "Application log: $APP_LOG"
echo "Response: $LOG_DIR/ingestion-response.json"
