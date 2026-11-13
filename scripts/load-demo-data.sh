#!/usr/bin/env bash
# Uploads every file in samples/ to a running instance, so the UI has something
# to answer questions about. Safe to run twice: documents that already exist are
# reported and skipped by the API with 409 Conflict.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SAMPLES_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../samples" && pwd)"

if ! curl -fsS "$BASE_URL/actuator/health" >/dev/null; then
  echo "No application at $BASE_URL. Start it with: mvn spring-boot:run" >&2
  exit 1
fi

for file in "$SAMPLES_DIR"/*; do
  [ -f "$file" ] || continue
  name="$(basename "$file")"
  title="${name%.*}"
  status="$(curl -s -o /dev/null -w '%{http_code}' \
    -F "file=@${file}" \
    -F "title=${title}" \
    "${BASE_URL}/api/documents/upload")"

  case "$status" in
    201) echo "loaded   ${name}" ;;
    409) echo "skipped  ${name} (already present)" ;;
    *)
      echo "failed   ${name} (HTTP ${status})" >&2
      exit 1
      ;;
  esac
done

echo
echo "Demo data ready. Ask something like:"
echo "  curl -s ${BASE_URL}/api/ask -H 'Content-Type: application/json' \\"
echo "    -d '{\"question\": \"How do I roll back a deployment?\"}'"
