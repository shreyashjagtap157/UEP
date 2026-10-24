#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TEMP_ROOT="$(mktemp -d)"
trap 'rm -rf -- "$TEMP_ROOT"' EXIT HUP INT TERM
MOCK_BIN="$TEMP_ROOT/bin"
mkdir -p "$MOCK_BIN"

cat > "$MOCK_BIN/psql" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
connection="${1:-}"
query=""
while (($#)); do
  if [[ "$1" == "-c" ]]; then
    shift
    query="${1:-}"
    break
  fi
  shift
done

if [[ "$query" == *"current_database()"* ]]; then
  case "$connection" in
    source-url) printf '%s\n' 'education|10.0.0.10|5432' ;;
    restore-url) printf '%s\n' 'education_restore|10.0.0.10|5432' ;;
    same-url) printf '%s\n' 'education|10.0.0.10|5432' ;;
    *) echo "Unexpected database connection string in test: $connection" >&2; exit 4 ;;
  esac
elif [[ "$query" == "SELECT count(*) FROM tenant;" ]]; then
  printf '%s\n' '3'
elif [[ "$query" == "SELECT count(*) FROM tenant_membership;" ]]; then
  printf '%s\n' '7'
elif [[ "$query" == "SELECT count(*) FROM flyway_schema_history WHERE success;" ]]; then
  printf '%s\n' '28'
else
  echo "Unexpected psql query in test: $query" >&2
  exit 5
fi
MOCK
chmod +x "$MOCK_BIN/psql"

cat > "$MOCK_BIN/pg_dump" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
: "${PG_DUMP_MARKER:?}"
touch "$PG_DUMP_MARKER"
output=""
for argument in "$@"; do
  case "$argument" in
    --file=*) output="${argument#--file=}" ;;
  esac
done
if [[ -z "$output" ]]; then
  echo "pg_dump mock received no --file argument" >&2
  exit 6
fi
printf '%s\n' 'mock custom-format archive' > "$output"
MOCK
chmod +x "$MOCK_BIN/pg_dump"

cat > "$MOCK_BIN/pg_restore" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
: "${PG_RESTORE_LOG:?}"
printf '%s\n' "$*" >> "$PG_RESTORE_LOG"
if [[ "${1:-}" == "--list" ]]; then
  [[ -s "${2:-}" ]]
  exit
fi
[[ " $* " == *" --exit-on-error "* ]]
[[ " $* " == *" --clean "* ]]
MOCK
chmod +x "$MOCK_BIN/pg_restore"

export PATH="$MOCK_BIN:$PATH"
export PG_DUMP_MARKER="$TEMP_ROOT/pg-dump-called"
export PG_RESTORE_LOG="$TEMP_ROOT/pg-restore.log"

# Missing destructive-target acknowledgement must fail before touching PostgreSQL.
if env -u RESTORE_TARGET_IS_DISPOSABLE \
    DATABASE_URL=source-url BACKUP_FILE="$TEMP_ROOT/no-consent.dump" RESTORE_DATABASE=restore-url \
    bash "$ROOT/scripts/backup-restore-drill.sh" >/dev/null 2>&1; then
  echo "expected missing restore acknowledgement to fail" >&2
  exit 1
fi
[[ ! -e "$PG_DUMP_MARKER" ]] || { echo "pg_dump ran without restore acknowledgement" >&2; exit 1; }

# Same source/restore database must be rejected before pg_dump or pg_restore can run.
if env RESTORE_TARGET_IS_DISPOSABLE=true \
    DATABASE_URL=source-url BACKUP_FILE="$TEMP_ROOT/same-db.dump" RESTORE_DATABASE=same-url \
    bash "$ROOT/scripts/backup-restore-drill.sh" >/dev/null 2>&1; then
  echo "expected same-database restore to fail" >&2
  exit 1
fi
[[ ! -e "$PG_DUMP_MARKER" ]] || { echo "pg_dump ran after same-database collision" >&2; exit 1; }

# Existing backup artifacts must never be overwritten.
EXISTING="$TEMP_ROOT/existing.dump"
printf '%s\n' 'preserve this backup' > "$EXISTING"
if env RESTORE_TARGET_IS_DISPOSABLE=true \
    DATABASE_URL=source-url BACKUP_FILE="$EXISTING" RESTORE_DATABASE=restore-url \
    bash "$ROOT/scripts/backup-restore-drill.sh" >/dev/null 2>&1; then
  echo "expected existing backup path to be rejected" >&2
  exit 1
fi
grep -q 'preserve this backup' "$EXISTING"
[[ ! -e "$PG_DUMP_MARKER" ]] || { echo "pg_dump ran despite existing backup" >&2; exit 1; }

# Happy path: the helper creates a private backup, restores with --exit-on-error, and verifies key tables.
BACKUP="$TEMP_ROOT/success.dump"
OUTPUT="$(env RESTORE_TARGET_IS_DISPOSABLE=true \
    DATABASE_URL=source-url BACKUP_FILE="$BACKUP" RESTORE_DATABASE=restore-url \
    bash "$ROOT/scripts/backup-restore-drill.sh")"
[[ -s "$BACKUP" ]]
[[ "$(stat -c '%a' "$BACKUP")" == "600" ]]
grep -q 'tenants=3 memberships=7 successful_migrations=28' <<< "$OUTPUT"
grep -q -- '--exit-on-error --clean --if-exists' "$PG_RESTORE_LOG"
echo "backup/restore guard tests passed"
