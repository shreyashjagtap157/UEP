#!/usr/bin/env bash
set -euo pipefail
umask 077

: "${DATABASE_URL:?DATABASE_URL must be set}"
: "${BACKUP_FILE:?BACKUP_FILE must be set}"
: "${RESTORE_DATABASE:?RESTORE_DATABASE must be set}"
: "${RESTORE_TARGET_IS_DISPOSABLE:?Set RESTORE_TARGET_IS_DISPOSABLE=true only for a disposable restore target}"
if [[ "$RESTORE_TARGET_IS_DISPOSABLE" != "true" ]]; then
  echo "RESTORE_TARGET_IS_DISPOSABLE must be exactly true; refusing a destructive restore" >&2
  exit 2
fi

if ! command -v pg_dump >/dev/null || ! command -v pg_restore >/dev/null || ! command -v psql >/dev/null; then
  echo "pg_dump, pg_restore, and psql are required" >&2
  exit 2
fi

# Fail closed before creating a backup or running pg_restore --clean. Comparing database names
# intentionally rejects same-name databases on different clusters too; use a uniquely named
# disposable restore database for the drill.
source_identity="$(psql "$DATABASE_URL" -X -v ON_ERROR_STOP=1 -At -c \
  "SELECT current_database() || '|' || COALESCE(inet_server_addr()::text, 'unix-socket') || '|' || current_setting('port')")"
restore_identity="$(psql "$RESTORE_DATABASE" -X -v ON_ERROR_STOP=1 -At -c \
  "SELECT current_database() || '|' || COALESCE(inet_server_addr()::text, 'unix-socket') || '|' || current_setting('port')")"
source_database="${source_identity%%|*}"
restore_database="${restore_identity%%|*}"
if [[ -z "$source_database" || -z "$restore_database" ]]; then
  echo "Could not determine source and restore database identity" >&2
  exit 2
fi
if [[ "$source_database" == "$restore_database" || "$source_identity" == "$restore_identity" ]]; then
  echo "Source and restore targets must be distinct; refusing destructive restore" >&2
  exit 2
fi

if [[ -e "$BACKUP_FILE" || -e "${BACKUP_FILE}.partial.$$" ]]; then
  echo "Backup path already exists; refusing to overwrite an existing backup: $BACKUP_FILE" >&2
  exit 2
fi
partial_backup="${BACKUP_FILE}.partial.$$"
trap 'rm -f -- "$partial_backup"' EXIT HUP INT TERM

pg_dump --format=custom --no-owner --no-acl --dbname="$DATABASE_URL" --file="$partial_backup"
pg_restore --list "$partial_backup" >/dev/null
chmod 600 "$partial_backup"
mv -- "$partial_backup" "$BACKUP_FILE"
trap - EXIT HUP INT TERM

# Restore only into the explicitly acknowledged disposable database; fail immediately on errors.
pg_restore --exit-on-error --clean --if-exists --no-owner --no-acl --dbname="$RESTORE_DATABASE" "$BACKUP_FILE"

tenant_count="$(psql "$RESTORE_DATABASE" -X -v ON_ERROR_STOP=1 -At -c 'SELECT count(*) FROM tenant;')"
membership_count="$(psql "$RESTORE_DATABASE" -X -v ON_ERROR_STOP=1 -At -c 'SELECT count(*) FROM tenant_membership;')"
migration_count="$(psql "$RESTORE_DATABASE" -X -v ON_ERROR_STOP=1 -At -c 'SELECT count(*) FROM flyway_schema_history WHERE success;')"
if [[ ! "$tenant_count" =~ ^[0-9]+$ || ! "$membership_count" =~ ^[0-9]+$ || ! "$migration_count" =~ ^[0-9]+$ || "$migration_count" -eq 0 ]]; then
  echo "Restore validation failed: counts were not readable or Flyway history is missing" >&2
  exit 1
fi

echo "backup/restore drill passed (tenants=$tenant_count memberships=$membership_count successful_migrations=$migration_count)"
