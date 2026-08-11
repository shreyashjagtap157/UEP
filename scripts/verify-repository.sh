#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

version="$(tr -d '\r\n' < VERSION)"
grep -Fq "<version>${version}</version>" apps/platform-server/pom.xml
grep -Fq "version: ${version}" docs/api/openapi.yaml
grep -Fq "version: ${version}" apps/platform-server/src/main/resources/application.yaml

if grep -RInE --exclude-dir=target --exclude-dir=node_modules 'if[[:space:]]*\([^\n]*(plan|tier)[[:space:]]*==' apps; then
  echo "Hard-coded commercial plan/tier conditional detected" >&2
  exit 1
fi

if grep -RInE --exclude-dir=target --exclude-dir=node_modules '(PASSWORD|TOKEN|SECRET|PRIVATE_KEY)[[:space:]]*=[[:space:]]*[^${][^[:space:]]+' apps infra .github 2>/dev/null; then
  echo "Potential hard-coded secret detected" >&2
  exit 1
fi

git diff --check
printf 'repository policy checks passed for %s\n' "$version"
