#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATTERN = re.compile(r"^(\d+)\.(\d+)\.(\d+)\.(\d+)(-SNAPSHOT)?$")

if len(sys.argv) != 2 or not PATTERN.fullmatch(sys.argv[1]):
    raise SystemExit("usage: scripts/set-version.py stable.major.minor.patch[-SNAPSHOT]")

version = sys.argv[1]
numeric = version.removesuffix("-SNAPSHOT")
npm_version = ".".join(numeric.split(".")[:3])

(ROOT / "VERSION").write_text(version + "\n", encoding="utf-8")

replacements = {
    ROOT / "apps/platform-server/pom.xml": (
        re.compile(r"(<artifactId>platform-server</artifactId>\s*<version>)[^<]+(</version>)"),
        rf"\g<1>{version}\2",
    ),
    ROOT / "apps/platform-server/src/main/resources/application.yaml": (
        re.compile(r"(?m)^(  version: ).+$"),
        rf"\g<1>{version}",
    ),
    ROOT / "docs/api/openapi.yaml": (
        re.compile(r"(?m)^(  version: ).+$"),
        rf"\g<1>{version}",
    ),
}

for path, (pattern, replacement) in replacements.items():
    text = path.read_text(encoding="utf-8")
    updated, count = pattern.subn(replacement, text, count=1)
    if count != 1:
        raise SystemExit(f"could not update version in {path}")
    path.write_text(updated, encoding="utf-8")

package_path = ROOT / "apps/web/package.json"
package = json.loads(package_path.read_text(encoding="utf-8"))
package["version"] = npm_version
package["productVersion"] = version
package_path.write_text(json.dumps(package, indent=2) + "\n", encoding="utf-8")
print(f"set product version to {version}")
