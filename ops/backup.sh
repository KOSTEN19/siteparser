#!/usr/bin/env bash
set -euo pipefail

TS=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR=${1:-"./backups"}
mkdir -p "${BACKUP_DIR}"

echo "Creating PostgreSQL backup..."
docker exec parsesite-postgres pg_dump -U parsesite parsesite > "${BACKUP_DIR}/postgres_${TS}.sql"

echo "Creating Elasticsearch snapshot repository..."
curl -s -X PUT "http://localhost:9200/_snapshot/parsesite_fs_repo" \
  -H "Content-Type: application/json" \
  -d '{"type":"fs","settings":{"location":"/tmp/es-backups","compress":true}}' >/dev/null || true

echo "Creating Elasticsearch snapshot..."
curl -s -X PUT "http://localhost:9200/_snapshot/parsesite_fs_repo/snap_${TS}?wait_for_completion=true" >/dev/null

echo "Backup done: ${BACKUP_DIR}"
