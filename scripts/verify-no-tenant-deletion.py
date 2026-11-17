#!/usr/bin/env python3
"""Guard: no production code path may delete a tenant.

Tenant lifecycle is status-based (ACTIVE/READ_ONLY/SUSPENDED/CLOSED); offboarding
is a status change (MembershipStatus.ENDED), never a row deletion. The notification
outbox poller takes the outbox row then the tenant row, while a DELETE FROM tenant
takes the tenant row then the cascaded outbox row, so a hard delete can deadlock
with the poller. That conflict is therefore only reachable from test teardown;
this guard keeps it that way as the codebase evolves.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "apps" / "platform-server" / "src" / "main"

PATTERNS = [
    # Direct DELETE FROM tenant/tenants
    re.compile(r"delete\s+from\s+tenant\b", re.IGNORECASE),
    re.compile(r"delete\s+from\s+tenants\b", re.IGNORECASE),
    # Method calls on tenant service/repository
    re.compile(r"\btenantService\.removeTenant\s*\(", re.IGNORECASE),
    re.compile(r"\btenantRepository\.delete(?:All|ById)?\s*\(", re.IGNORECASE),
    re.compile(r"\btenantRepo\.delete(?:All|ById)?\s*\(", re.IGNORECASE),
    # SQL DELETE FROM on tables with tenant in name
    re.compile(r"DELETE\s+FROM\s+\w*tenant\w*\b", re.IGNORECASE),
]

def scan(root: Path, suffixes: tuple[str, ...]) -> list[str]:
    hits: list[str] = []
    for path in root.rglob("*"):
        if path.is_dir() or path.suffix not in suffixes:
            continue
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        for lineno, line in enumerate(text.splitlines(), 1):
            for pattern in PATTERNS:
                if pattern.search(line):
                    hits.append(f"{path.relative_to(ROOT)}:{lineno}: {line.strip()}")
    return hits

def main() -> int:
    hits = scan(MAIN, (".java", ".sql", ".yaml", ".yml", ".properties"))
    if hits:
        print("Production tenant-deletion path detected (forbidden):", file=sys.stderr)
        for hit in hits:
            print(f"  {hit}", file=sys.stderr)
        print(
            "Tenant lifecycle is status-based; deletion is only permitted in test "
            "teardown. See docs/IMPLEMENTATION_STATUS.md.",
            file=sys.stderr,
        )
        return 1
    print("no production tenant-deletion paths detected")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())