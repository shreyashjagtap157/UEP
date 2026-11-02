#!/usr/bin/env python3
"""Verify retired hosted deployment entry points and the remaining staging test boundary."""
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
staging = (root / ".github/workflows/staging-e2e.yml").read_text(encoding="utf-8")
playwright = (root / "apps/web/playwright.staging.config.ts").read_text(encoding="utf-8")
runbook = (root / "docs/operations/FLY_SUPABASE_STAGING.md").read_text(encoding="utf-8")
local_guide = (root / "docs/operations/LOCAL_NATIVE_WINDOWS.md").read_text(encoding="utf-8")
migration_workflow = root / ".github/workflows/database-migration.yml"
api_fly_manifest = root / "apps/platform-server/fly.toml"
keycloak_fly_manifest = root / "infra/keycloak/fly.toml"

checks = {
    "hosted database migration workflow is removed": not migration_workflow.exists(),
    "Fly API deployment manifest is removed": not api_fly_manifest.exists(),
    "Fly Keycloak deployment manifest is removed": not keycloak_fly_manifest.exists(),
    "hosted staging runbook is explicitly retired": "STATUS: RETIRED" in runbook and "NOT DEPLOYABLE" in runbook,
    "runbook records Supabase as inactive": "status INACTIVE" in runbook,
    "runbook records stopped Fly machine and missing Keycloak app": (
        "d89565db307d58" in runbook and "Keycloak app uep-keycloak-testing: does not exist" in runbook
    ),
    "runbook directs users to local native path": "LOCAL_NATIVE_WINDOWS.md" in runbook,
    "local native guide is present": (root / "scripts/Start-UEP.ps1").is_file() and local_guide,
    "remaining remote staging workflow is manual only": (
        "workflow_dispatch:" in staging
        and "on:\n  push:" not in staging
        and "pull_request:" not in staging
        and "workflow_run:" not in staging
    ),
    "remaining staging workflow only launches Playwright tests": (
        "npx playwright test" in staging
        and "fly deploy" not in staging.lower()
        and "supabase db push" not in staging.lower()
    ),
    "staging Playwright traces are disabled": "trace: 'off'" in playwright,
    "staging screenshots and video are disabled": (
        "screenshot: 'off'" in playwright and "video: 'off'" in playwright
    ),
}

failed = [name for name, passed in checks.items() if not passed]
for name, passed in checks.items():
    print(f"{'PASS' if passed else 'FAIL'}: {name}")
if failed:
    print(f"Hosted retirement contract failed ({len(failed)} checks).", file=sys.stderr)
    raise SystemExit(1)
print(f"Hosted retirement contract passed ({len(checks)} checks).")
