#!/usr/bin/env bash
# verify-lock-order.sh - GitOps-driven lock-order invariant check
#
# Ensures that any new method acquiring a tenant lock in the outbox/poll path
# uses the "tenant-before-outbox" pattern:  SELECT ... JOIN tenant ... FOR KEY SHARE OF t
#
# Failure if:
#   - A new or modified method in NotificationOutboxProcessor (or related
#     production services) acquires a tenant lock without the single-JOIN
#     FOR KEY SHARE OF t pattern, OR
#   - The tenant-first lock-order pattern is removed from production code.
#
# Files that manage status-only lifecycle (ACTIVE→SUSPENDED→CLOSED) via
# TenantOffboarding.java are exempt – they never use FOR KEY SHARE OF t.
#
# Usage: bash scripts/verify-lock-order.sh
# Exit code 0 = invariant preserved, 1 = invariant risk detected

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Gather changed Java files (staged + working tree + new untracked)
if git diff --cached --name-only --diff-filter=ACM 2>/dev/null | grep -q '\.java$'; then
    CHANGED_FROM_STAGED=$(git diff --cached --name-only --diff-filter=ACM | grep '\.java$' || true)
else
    CHANGED_FROM_STAGED=""
fi

if git diff --name-only --diff-filter=ACM 2>/dev/null | grep -q '\.java$'; then
    CHANGED_FROM_WORKING=$(git diff --name-only --diff-filter=ACM | grep '\.java$' || true)
else
    CHANGED_FROM_WORKING=""
fi

NEW_JAVA=$(git ls-files --others --exclude-standard | grep '\.java$' || true)

ALL_CHANGED_JAVA=""
if [ -n "$CHANGED_FROM_STAGED" ]; then
    ALL_CHANGED_JAVA="$CHANGED_FROM_STAGED"
fi
if [ -n "$CHANGED_FROM_WORKING" ] && [ -n "$ALL_CHANGED_JAVA" ]; then
    ALL_CHANGED_JAVA="$ALL_CHANGED_JAVA"$'\n'"$CHANGED_FROM_WORKING"
elif [ -n "$CHANGED_FROM_WORKING" ]; then
    ALL_CHANGED_JAVA="$CHANGED_FROM_WORKING"
fi
if [ -n "$NEW_JAVA" ] && [ -n "$ALL_CHANGED_JAVA" ]; then
    ALL_CHANGED_JAVA="$ALL_CHANGED_JAVA"$'\n'"$NEW_JAVA"
elif [ -n "$NEW_JAVA" ]; then
    ALL_CHANGED_JAVA="$NEW_JAVA"
fi

# Exit early if no Java files changed
if [ -z "$ALL_CHANGED_JAVA" ]; then
    echo "No changed Java files detected; lock-order invariant is unchanged."
    exit 0
fi

# Production-only: only check files under src/main (not src/test)
MAIN_JAVA=""
for f in $(echo "$ALL_CHANGED_JAVA"); do
    relpath="${f#$PROJECT_ROOT/}"
    if [[ "$relpath" =~ ^apps/platform-server/src/main/ ]]; then
        MAIN_JAVA="$MAIN_JAVA"$'\n'"$f"
    fi
done

if [ -z "$MAIN_JAVA" ]; then
    echo "No changed production Java files detected; lock-order invariant is unchanged."
    exit 0
fi

RISK=0
RISK_FILES=()

# Files that are exempt from the lock-order check because they manage
# status transitions (ACTIVE→SUSPENDED→CLOSED) without database row locks
EXEMPT_NAMES=(
    "Tenant.java" "TenantRepository.java" "TenantOffboarding.java"
    "package-info.java"
)

for filepath in $(echo "$MAIN_JAVA"); do
    relpath="${filepath#$PROJECT_ROOT/}"
    basename_file="$(basename "$filepath")"
    echo "Checking lock-order invariant in: $relpath"

    # Skip exempt files (status/entity/utility classes)
    IS_EXEMPT=false
    for exempt in "${EXEMPT_NAMES[@]}"; do
        if [[ "$basename_file" == "$exempt" ]]; then
            IS_EXEMPT=true
            break
        fi
    done

    if [ "$IS_EXEMPT" = true ]; then
        echo "  ℹ️  Exempt file (status/entity/utility); no DB lock pattern expected"
        continue
    fi

    # Check for the safe lock-order pattern
    if grep -q "FOR KEY SHARE OF t" "$filepath"; then
        echo "  ✅  Lock-order pattern present in $relpath"
    else
        # Check if this file contains lock-relevant methods
        if grep -qE "lockById|lockTenant|SELECT.*FROM|FOR UPDATE|FOR SHARE" "$filepath"; then
            echo "  ⚠️  RISK: No 'FOR KEY SHARE OF t' pattern found in $relpath "
            echo "       but file contains lock-relevant SQL patterns"
            RISK=1
            RISK_FILES+=("$filepath")
        else
            echo "  ℹ️  No lock-relevant patterns found; skipping lock-order check"
        fi
    fi
done

# Critical: NotificationOutboxProcessor.java must have the tenant-first pattern
NOTIFICATION_OUTBOX_PROCESSOR="$PROJECT_ROOT/apps/platform-server/src/main/java/com/universalplatform/notification/NotificationOutboxProcessor.java"
if [ -f "$NOTIFICATION_OUTBOX_PROCESSOR" ]; then
    if grep -q "lockTenant" "$NOTIFICATION_OUTBOX_PROCESSOR"; then
        if ! grep -q "FOR KEY SHARE OF t" "$NOTIFICATION_OUTBOX_PROCESSOR"; then
            echo "  🚨 CRITICAL: NotificationOutboxProcessor.lockTenant() lacks 'FOR KEY SHARE OF t' pattern!"
            RISK=1
            RISK_FILES+=("$NOTIFICATION_OUTBOX_PROCESSOR")
        else
            echo "  ✅  NotificationOutboxProcessor.lockTenant() has the tenant-first lock-order pattern"
        fi
    fi
fi

if [ "$RISK" -eq 1 ]; then
    echo ""
    echo "Lock-order invariant at risk in the following files:"
    for f in "${RISK_FILES[@]}"; do
        echo "  - $f"
    done
    echo ""
    echo "Ensure tenant-before-outbox ordering: SELECT ... JOIN tenant ... FOR KEY SHARE OF t"
    exit 1
fi

echo "Lock-order invariant preserved across all changed production files."
exit 0