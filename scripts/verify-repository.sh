#!/bin/bash -eu
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

# Hard-coded secret heuristics: KEY=value style (shell/env/YAML-style patterns),
# plus YAML colon style in application.yaml. Known development placeholders and
# infra/ dev-only compose values are tolerated; production must fail fast on
# dev defaults via the platform boot guard instead.
secret_hits="$(grep -RInE --exclude-dir=target --exclude-dir=node_modules '(PASSWORD|TOKEN|SECRET|PRIVATE_KEY|API_KEY)[[:space:]]*[:=][[:space:]]*[^${"'"'"'[:space:]][^[:space:]]+' apps .github 2>/dev/null \
  | grep -Fv '${' \
  | grep -v 'change-me-in-production' \
  | grep -v 'devkey' \
  | grep -vE '[:=][[:space:]]*education([[:space:]]|$)' \
  || true)"
if [ -n "$secret_hits" ]; then
  echo "Potential hard-coded secret detected:" >&2
  echo "$secret_hits" >&2
  exit 1
fi

python3 scripts/verify-source-structure.py
python3 scripts/verify-contract-parity.py
python3 scripts/qualify-0.15.py

git diff --check
printf 'repository policy checks passed for %s\n' "$version"
