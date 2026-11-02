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


def mask_java_comments_and_literals(source: str) -> str:
    """Replace comments and literals with spaces while preserving code braces/newlines."""
    output = list(source)
    state = "code"
    index = 0

    def blank(position: int) -> None:
        if output[position] not in "\r\n":
            output[position] = " "

    while index < len(source):
        char = source[index]
        next_char = source[index + 1] if index + 1 < len(source) else ""

        if state == "code":
            if source.startswith("//", index):
                blank(index)
                blank(index + 1)
                state = "line_comment"
                index += 2
                continue
            if source.startswith("/*", index):
                blank(index)
                blank(index + 1)
                state = "block_comment"
                index += 2
                continue
            if source.startswith('"""', index):
                for offset in range(3):
                    blank(index + offset)
                state = "text_block"
                index += 3
                continue
            if char == '"':
                blank(index)
                state = "string"
                index += 1
                continue
            if char == "'":
                blank(index)
                state = "character"
                index += 1
                continue
        elif state == "line_comment":
            if char in "\r\n":
                state = "code"
            else:
                blank(index)
        elif state == "block_comment":
            blank(index)
            if char == "*" and next_char == "/":
                blank(index + 1)
                index += 2
                state = "code"
                continue
        elif state in {"string", "character"}:
            blank(index)
            if char == "\\" and index + 1 < len(source):
                blank(index + 1)
                index += 2
                continue
            if (state == "string" and char == '"') or (state == "character" and char == "'"):
                state = "code"
        elif state == "text_block":
            if char == "\\" and index + 1 < len(source):
                blank(index)
                blank(index + 1)
                index += 2
                continue
            if source.startswith('"""', index):
                for offset in range(3):
                    blank(index + offset)
                index += 3
                state = "code"
                continue
            blank(index)

        index += 1

    return "".join(output)


def duplicate_method_errors(path: Path, root: Path) -> list[str]:
    """Find repeated signatures only within the same lexical enclosing block.

    Java methods cannot be declared inside method bodies. The nearest open block at
    a method declaration is therefore its containing type body, including anonymous
    classes. Tracking each block independently avoids conflating overrides declared
    in separate anonymous TransactionSynchronization implementations.
    """
    source = path.read_text(encoding="utf-8")
    masked_lines = mask_java_comments_and_literals(source).splitlines()
    scopes: list[int] = []
    next_scope = 1
    signatures: dict[tuple[int, str, str], list[int]] = defaultdict(list)

    for number, line in enumerate(masked_lines, 1):
        match = METHOD.match(line)
        if match:
            return_type, name, params = match.groups()
            if return_type not in CONTROL_WORDS:
                normalized_params = re.sub(r"\s+", " ", params.strip())
                enclosing_scope = scopes[-1] if scopes else 0
                signatures[(enclosing_scope, name, normalized_params)].append(number)

        for char in line:
            if char == "{":
                scopes.append(next_scope)
                next_scope += 1
            elif char == "}" and scopes:
                scopes.pop()

    errors: list[str] = []
    for (_, name, params), declaration_lines in signatures.items():
        if len(declaration_lines) > 1:
            joined = ", ".join(map(str, declaration_lines))
            errors.append(
                f"{path.relative_to(root)}:{joined}: duplicate method declaration {name}({params})"
            )
    return errors


def main() -> int:
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
                    if stripped.count("(") <= stripped.count(")") and not stripped.endswith('(""")'):
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

            errors.extend(duplicate_method_errors(path, ROOT))

    if errors:
        print("source structure verification failed:", file=sys.stderr)
        for error in errors:
            print(f"  {error}", file=sys.stderr)
        return 1

    print("source structure checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
