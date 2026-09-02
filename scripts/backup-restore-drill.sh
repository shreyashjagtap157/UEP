#!/usr/bin/env bash
set -euo pipefail
: "${DATABASE_URL:?DATABASE_URL must be set}"
: "${BACKUP_FILE:?BACKUP_FILE must be set}"
: "${RESTORE_DATABASE:?RESTORE_DATABASE must be set}"

if ! command -v pg_dump >/dev/null || ! command -v pg_restore >/dev/null; then
  echo "pg_dump and pg_restore are required" >&2
  exit 2
fi
pg_dump --format=custom --no-owner --no-acl --dbname="$DATABASE_URL" --file="$BACKUP_FILE"
pg_restore --list "$BACKUP_FILE" >/dev/null
pg_restore --clean --if-exists --no-owner --no-acl --dbname="$RESTORE_DATABASE" "$BACKUP_FILE"
psql "$RESTORE_DATABASE" -v ON_ERROR_STOP=1 -c 'SELECT count(*) AS tenant_count FROM tenant;' >/dev/null
echo "backup/restore drill passed"
