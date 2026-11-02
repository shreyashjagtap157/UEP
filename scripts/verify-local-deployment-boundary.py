#!/usr/bin/env python3
"""Fail-closed checks for the retired hosted backend/database deployment boundary."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
RUNBOOK = ROOT / "docs/operations/FLY_SUPABASE_STAGING.md"
LOCAL_GUIDE = ROOT / "docs/operations/LOCAL_NATIVE_WINDOWS.md"
CLOUDFLARE_GUIDE = ROOT / "docs/CLOUDFLARE_DEMO_DEPLOY.md"
VITE = ROOT / "apps/web/vite.config.ts"
PACKAGE = ROOT / "apps/web/package.json"

retired_paths = [
    ".github/workflows/database-migration.yml",
    ".github/workflows/staging-e2e.yml",
    "apps/platform-server/fly.toml",
    "infra/keycloak/fly.toml",
    "apps/platform-server/src/main/resources/application-vercel.yaml",
    "vercel.json",
    "scripts/verify-staging-workflow.py",
    "scripts/verify-vercel-config.py",
    "apps/web/scripts/guard-vercel-build.mjs",
]

runbook = RUNBOOK.read_text(encoding="utf-8")
local = LOCAL_GUIDE.read_text(encoding="utf-8")
cloudflare = CLOUDFLARE_GUIDE.read_text(encoding="utf-8")
vite = VITE.read_text(encoding="utf-8")
package = PACKAGE.read_text(encoding="utf-8")

checks = {
    "all hosted backend/database deploy entry points are absent": all(
        not (ROOT / path).exists() for path in retired_paths
    ),
    "retirement record is clearly non-deployable": (
        "STATUS: RETIRED" in runbook and "NOT DEPLOYABLE" in runbook
    ),
    "retirement record captures the stopped Fly machine and absent Keycloak app": (
        "d89565db307d58" in runbook
        and "Keycloak app uep-keycloak-testing: does not exist" in runbook
    ),
    "retirement record confirms the Supabase project was paused": (
        "status INACTIVE" in runbook and "successfully paused" in runbook
    ),
    "retirement record is honest about remaining provider-side deletion": (
        "Deletion of the stopped Fly machine and Fly app record" in runbook
        and "Permanent deletion of the paused Supabase project" in runbook
    ),
    "native Windows guide is the active replacement": (
        LOCAL_GUIDE.is_file()
        and "scripts/Start-UEP.ps1" in local
        and "No Docker" in local
    ),
    "Real frontend proxies API requests over IPv4 loopback": (
        "'/api'" in vite and "target: 'http://127.0.0.1:8080'" in vite
    ),
    "Cloudflare guide describes a frontend-only offline demo": (
        "VITE_LOCAL_MODE=true" in cloudflare
        and "does not require" in cloudflare.lower()
        and "backend" in cloudflare.lower()
    ),
    "the frontend no longer advertises a retired Vercel build command": (
        '"build:vercel"' not in package and '"build:production"' in package
    ),
    "hosted backend remains distinct from local API test configuration": (
        "http://127.0.0.1:8080" in local and "http://localhost:5173" in local
    ),
}

failed = [name for name, passed in checks.items() if not passed]
for name, passed in checks.items():
    print(f"{'PASS' if passed else 'FAIL'}: {name}")
if failed:
    print(f"Hosted deployment retirement contract failed ({len(failed)} checks).", file=sys.stderr)
    raise SystemExit(1)
print(f"Hosted deployment retirement contract passed ({len(checks)} checks).")
