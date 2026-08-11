#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def java_enum(path: Path) -> set[str]:
    text = path.read_text(encoding="utf-8")
    match = re.search(r"enum\s+\w+\s*\{(.*?)\}", text, re.S)
    if not match:
        raise ValueError(f"enum body not found in {path.relative_to(ROOT)}")
    return set(re.findall(r"\b([A-Z][A-Z0-9_]+)\b", match.group(1)))


def ts_union(path: Path, name: str) -> set[str]:
    text = path.read_text(encoding="utf-8")
    match = re.search(rf"export type {re.escape(name)}\s*=\s*(.*?)(?=\nexport\s)", text, re.S)
    if not match:
        raise ValueError(f"TypeScript union {name} not found in {path.relative_to(ROOT)}")
    return set(re.findall(r"'([A-Z][A-Z0-9_]+)'", match.group(1)))


def openapi_enum(path: Path, name: str) -> set[str]:
    lines = path.read_text(encoding="utf-8").splitlines()
    schema_start = None
    for index, line in enumerate(lines):
        if line == f"    {name}:":
            schema_start = index
            break
    if schema_start is None:
        raise ValueError(f"OpenAPI schema {name} not found")
    values: set[str] = set()
    in_enum = False
    for line in lines[schema_start + 1:]:
        if line.startswith("    ") and not line.startswith("      "):
            break
        if line.strip() == "enum:":
            in_enum = True
            continue
        if in_enum:
            match = re.match(r"\s*-\s+([A-Z][A-Z0-9_]+)\s*$", line)
            if match:
                values.add(match.group(1))
            elif line.strip() and not line.lstrip().startswith("-"):
                break
    if not values:
        raise ValueError(f"OpenAPI enum values for {name} not found")
    return values


def require_equal(label: str, *sets: tuple[str, set[str]]) -> None:
    baseline_name, baseline = sets[0]
    for name, values in sets[1:]:
        if values != baseline:
            missing = sorted(baseline - values)
            extra = sorted(values - baseline)
            raise ValueError(f"{label} mismatch: {name} missing={missing} extra={extra} vs {baseline_name}")
    print(f"{label} parity passed ({len(baseline)} values)")


try:
    api = ROOT / "docs" / "api" / "openapi.yaml"
    web = ROOT / "apps" / "web" / "src" / "platform" / "api.ts"
    permissions = java_enum(ROOT / "apps" / "platform-server" / "src" / "main" / "java" / "com" / "universalplatform" / "identity" / "PermissionKey.java")
    require_equal("permission contract", ("Java", permissions), ("TypeScript", ts_union(web, "PermissionKey")), ("OpenAPI", openapi_enum(api, "PermissionKey")))
    events = java_enum(ROOT / "apps" / "platform-server" / "src" / "main" / "java" / "com" / "universalplatform" / "notification" / "NotificationEventType.java")
    require_equal("notification event contract", ("Java", events), ("TypeScript", ts_union(web, "NotificationEventType")), ("OpenAPI", openapi_enum(api, "NotificationEventType")))
except (OSError, ValueError) as exc:
    print(f"contract parity verification failed: {exc}", file=sys.stderr)
    raise SystemExit(1)
