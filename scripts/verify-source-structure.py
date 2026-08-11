#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOTS = (
    ROOT / "apps" / "platform-server" / "src" / "main" / "java",
    ROOT / "apps" / "platform-server" / "src" / "test" / "java",
)

METHOD = re.compile(
    r"^\s*(?:(?:public|protected|private|static|final|synchronized|abstract|native|default|strictfp)\s+)*"
    r"(?:<[^>]+>\s+)?"
    r"([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*(?:<[^;{}()]+>)?(?:\[\])?)\s+"
    r"([A-Za-z_$][\w$]*)\s*\(([^)]*)\)\s*(?:throws\s+[^;{]+)?[;{]"
)
CONTROL_WORDS = {"return", "throw", "new", "if", "for", "while", "switch", "catch", "case", "else", "do", "try"}

errors: list[str] = []
for root in JAVA_ROOTS:
    if not root.exists():
        continue
    for path in sorted(root.rglob("*.java")):
        lines = path.read_text(encoding="utf-8").splitlines()

        # Once a @Query annotation has closed, another @Query may not appear before
        # its repository method declaration. This catches accidentally stacked query
        # annotations while supporting multiline text-block queries.
        query_open = False
        awaiting_method = False
        first_query_line: int | None = None
        for number, line in enumerate(lines, 1):
            stripped = line.strip()
            if stripped.startswith("@Query"):
                if awaiting_method:
                    errors.append(
                        f"{path.relative_to(ROOT)}:{number}: @Query appears before the method for @Query at line {first_query_line}"
                    )
                query_open = True
                first_query_line = number
                # Single-line @Query("...") forms close immediately.
                if stripped.count("(") <= stripped.count(")") and not stripped.endswith('("""'):
                    query_open = False
                    awaiting_method = True
                continue
            if query_open:
                if stripped.endswith('")') or stripped.endswith('""")'):
                    query_open = False
                    awaiting_method = True
                continue
            if awaiting_method:
                if not stripped or stripped.startswith("@"):
                    continue
                # The next declaration belongs to the query. We only care that it is
                # not another @Query; syntax/typing is checked by Java compilation.
                awaiting_method = False

        signatures: dict[tuple[str, str], list[int]] = defaultdict(list)
        for number, line in enumerate(lines, 1):
            match = METHOD.match(line)
            if not match:
                continue
            return_type, name, params = match.groups()
            if return_type in CONTROL_WORDS:
                continue
            normalized_params = re.sub(r"\s+", " ", params.strip())
            signatures[(name, normalized_params)].append(number)
        for (name, params), declaration_lines in signatures.items():
            if len(declaration_lines) > 1:
                joined = ", ".join(map(str, declaration_lines))
                errors.append(
                    f"{path.relative_to(ROOT)}:{joined}: duplicate method declaration {name}({params})"
                )

if errors:
    print("source structure verification failed:", file=sys.stderr)
    for error in errors:
        print(f"  {error}", file=sys.stderr)
    raise SystemExit(1)

print("source structure checks passed")
