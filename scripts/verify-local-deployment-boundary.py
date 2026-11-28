#!/usr/bin/env python3
"""Fail-closed checks for the retired hosted backend/database deployment boundary."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
RUNBOOK = ROOT / "docs/operations/FLY_SUPABASE_STAGING.md"
LOCAL_GUIDE = ROOT / "docs/operations/LOCAL_NATIVE_WINDOWS.md"
CLOUDFLARE_GUIDE = ROOT / "docs/CLOUDFLARE_DEMO_DEPLOY.md"
OCI_GUIDE = ROOT / "docs/operations/OCI_CLOUDFLARE_STAGING.md"
ACTUAL_STAGING = ROOT / "docs/operations/ACTUAL_STAGING_DEPLOYMENT.md"
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
oci = OCI_GUIDE.read_text(encoding="utf-8")
actual_staging = ACTUAL_STAGING.read_text(encoding="utf-8")
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
        and "Keycloak app uep-keycloak-testing does not exist" in runbook
    ),
    "retirement record confirms the Supabase project was paused": (
        "reported INACTIVE" in runbook and "pause operation returned success" in runbook
    ),
    "retirement record is honest about remaining provider-side deletion": (
        "stopped machine and app record still exist" in runbook
        and "project is paused, not permanently deleted" in runbook
    ),
    "native Windows guide is the active replacement": (
        LOCAL_GUIDE.is_file()
        and "scripts/Start-UEP.ps1" in local
        and "does not use docker" in local.lower()
    ),
    "Real frontend proxies API requests over IPv4 loopback": (
        "'/api'" in vite and "target: 'http://127.0.0.1:8080'" in vite
    ),
    "Cloudflare guide describes a frontend-only offline demo": (
        "VITE_LOCAL_MODE=true" in cloudflare
        and "offline" in cloudflare.lower() and "does not deploy or provision" in cloudflare.lower()
        and "backend" in cloudflare.lower()
    ),
    "Cloudflare local alternative uses SQLite and the non-Keycloak local-native path": (
        ".\\startuep.ps1 -Mode Real -Database SQLite" in cloudflare
        and "does not start or contact Keycloak" in cloudflare
        and "LOCAL_NATIVE_WINDOWS.md" in cloudflare
    ),
    "retired Fly/Supabase local replacement describes SQLite default and no Keycloak": (
        "SQLite is the default" in runbook
        and "does not start or contact Keycloak" in runbook
        and "Demo mode is separate" in runbook
    ),
    "retired OCI proposal points to the actual non-Keycloak local topology": (
        "SQLite by default" in oci
        and "Keycloak is not started or contacted" in oci
        and "Offline Demo" in oci
    ),
    "old hosted staging proposal cannot present itself as deployment authority": (
        "STATUS: SUPERSEDED / NOT DEPLOYABLE" in actual_staging
        and "historical hosted-staging proposal only" in actual_staging
        and "This is the deployment authority" not in actual_staging
        and "Historical proposed architecture" in actual_staging
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
