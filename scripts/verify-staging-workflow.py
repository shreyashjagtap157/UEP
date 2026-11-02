#!/usr/bin/env python3
"""Fail closed if the deployed staging workflow weakens its trust boundary."""
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
workflow = (root / ".github/workflows/staging-e2e.yml").read_text(encoding="utf-8")
playwright = (root / "apps/web/playwright.staging.config.ts").read_text(encoding="utf-8")
identity_test = (root / "apps/web/test/staging/identity.staging.spec.ts").read_text(encoding="utf-8")
runbook = (root / "docs/operations/OCI_CLOUDFLARE_STAGING.md").read_text(encoding="utf-8")

checks = {
    "manual dispatch remains available": "workflow_dispatch:" in workflow,
    "automatic workflow_run trigger is absent": "workflow_run:" not in workflow,
    "pull-request-target trigger is absent": "pull_request_target:" not in workflow,
    "dispatch is constrained to main": "github.ref == 'refs/heads/main'" in workflow,
    "checkout is pinned to main": "ref: main" in workflow,
    "staging Environment is used": "environment: staging" in workflow,
    "staging secrets are not job-global": "secrets." not in workflow.split("    steps:", 1)[0],
    "artifact retention is short": "retention-days: 7" in workflow,
    "browser traces are disabled": "trace: 'off'" in playwright,
    "failure screenshots are disabled": "screenshot: 'off'" in playwright,
    "browser video is disabled": "video: 'off'" in playwright,
    "tenant UUID assertion is report-safe": (
        "expect(identity.tenantId === account.tenantId" in identity_test
        and "expect(identity.tenantId).toBe(account.tenantId)" not in identity_test
    ),
    "independent GitHub Environment branch restriction is documented": (
        "GitHub Environment" in runbook and "main-only" in runbook
    ),
}

failed = [name for name, passed in checks.items() if not passed]
for name, passed in checks.items():
    print(f"{'PASS' if passed else 'FAIL'}: {name}")
if failed:
    print(f"Staging workflow security contract failed ({len(failed)} checks).", file=sys.stderr)
    raise SystemExit(1)
print(f"Staging workflow security contract passed ({len(checks)} checks).")
