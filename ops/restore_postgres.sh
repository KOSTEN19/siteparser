#!/usr/bin/env bash
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "Usage: $0 <backup-file.sql>"
  exit 1
fi

BACKUP_FILE="$1"
cat "${BACKUP_FILE}" | docker exec -i parsesite-postgres psql -U parsesite -d parsesite
echo "Restore complete"
