#!/usr/bin/env python3
"""Static contract checks for the native Windows UEP launcher.

This does not execute Windows services. CI separately parses the PowerShell source;
the real Windows acceptance run must still be performed on a developer workstation.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
launcher_path = ROOT / "scripts" / "Start-UEP.ps1"
wrapper_path = ROOT / "startuep.ps1"
vite_path = ROOT / "apps" / "web" / "vite.config.ts"
pom_path = ROOT / "apps" / "platform-server" / "pom.xml"
migration_path = ROOT / "apps" / "platform-server" / "src" / "main" / "java" / "com" / "universalplatform" / "DatabaseMigrationRunner.java"
workflow_path = ROOT / ".github" / "workflows" / "ci.yml"

launcher = launcher_path.read_text(encoding="utf-8")
wrapper = wrapper_path.read_text(encoding="utf-8")
vite = vite_path.read_text(encoding="utf-8")
pom = pom_path.read_text(encoding="utf-8")
migration = migration_path.read_text(encoding="utf-8")
workflow = workflow_path.read_text(encoding="utf-8")

checks = {
    "launcher supports Demo, Real, Both and Presentation modes": (
        "ValidateSet('Demo', 'Real', 'Both', 'Presentation')" in launcher
        and "ValidateSet('Demo', 'Real', 'Both', 'Presentation')" in wrapper
        and "PRESENTATION MODE" in launcher
    ),
    "launcher supports SQLite and PostgreSQL local database modes": (
        "ValidateSet('SQLite', 'PostgreSQL')" in launcher
        and "function Initialize-LocalSqliteDatabase" in launcher
        and "function Initialize-LocalDatabase" in launcher
    ),
    "SQLite mode bypasses PostgreSQL install and Flyway": (
        "if ($Database -eq 'PostgreSQL') { Ensure-Postgres }" in launcher
        and "if ($Database -eq 'SQLite') {\n            Initialize-LocalSqliteDatabase" in launcher
        and "set SPRING_PROFILES_ACTIVE=local-sqlite" in launcher
        and "set PLATFORM_BACKGROUND_WORKERS_ENABLED=false" in launcher
    ),
    "SQLite Keycloak uses an isolated development database": (
        "set KC_DB=dev-file" in launcher and "KC_DB_SCHEMA=keycloak" in launcher
    ),
    "Demo mode enables local fixture authentication": "set VITE_LOCAL_MODE=true" in launcher,
    "Real mode disables local fixture authentication": "set VITE_LOCAL_MODE=false" in launcher,
    "Real mode configures the tracked Keycloak realm and client": (
        "VITE_KEYCLOAK_REALM=education" in launcher
        and "VITE_KEYCLOAK_CLIENT_ID=platform-web" in launcher
        and "VITE_KEYCLOAK_URL=http://localhost:8081" in launcher
    ),
    "Real mode leaves the API base URL same-origin for Vite proxying": "set VITE_BACKEND_URL=" in launcher,
    "Vite proxies API requests to the IPv4 loopback backend": (
        "'/api'" in vite and "target: 'http://127.0.0.1:8080'" in vite
    ),
    "backend listens on the configured local API port": "set PORT=8080" in launcher,
    "frontend and backend have explicit local endpoints": (
        "http://localhost:5173" in launcher and "http://localhost:8080" in launcher
    ),
    "Both and Presentation modes run separately labelled Real and Demo apps": (
        "5174" in launcher
        and "set VITE_LOCAL_MODE=false" in launcher
        and "set VITE_LOCAL_MODE=true" in launcher
        and "VITE_UEP_PRESENTATION_LABEL=REAL UEP - LOCAL " in launcher
        and "VITE_UEP_PRESENTATION_LABEL=OFFLINE DEMO - SEEDED BROWSER DATA" in launcher
        and "Start-Process 'http://localhost:5173'; Start-Process 'http://localhost:5174'" in launcher
    ),
    "PostgreSQL is provisioned without a container engine": (
        "PostgreSQL 18.4" in launcher and "pg_isready.exe" in launcher
        and not re.search(r"\b(?:docker|podman)\s+(?:run|compose|build)\b", launcher, re.I)
    ),
    "Keycloak is run from the native distribution": (
        "keycloak-26.7.5.zip" in launcher and "kc.bat" in launcher
        and "start-dev --http-port=8081 --import-realm" in launcher
    ),
    "Maven installation verifies the downloaded SHA-512": (
        "apache-maven-3.10.0-bin.zip" in launcher and "Get-FileHash" in launcher
        and "SHA512" in launcher
    ),
    "Keycloak archive verifies the pinned SHA-256": (
        "30ef87fb7101c43d29688d1ae80c0588ff4a151b3330e9f664bf9692d398658a" in launcher
        and "Algorithm SHA256" in launcher
    ),
    "local database uses distinct migrator, runtime and Keycloak roles": (
        all(name in launcher for name in (
            "uep_local_migrator", "uep_local_runtime", "uep_local_keycloak"
        ))
    ),
    "Keycloak owns a separate schema in PostgreSQL mode": "KC_DB_SCHEMA=keycloak" in launcher
        and "CREATE SCHEMA keycloak AUTHORIZATION " in launcher,
    "database migration runner is compiled before execution": (
        "compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java" in launcher
    ),
    "database migrations use the explicit runner": (
        "org.codehaus.mojo:exec-maven-plugin:3.5.0:java" in launcher
        and all(name in launcher for name in (
            "UEP_DATABASE_MIGRATION_JDBC_URL",
            "UEP_DATABASE_MIGRATION_USERNAME",
            "UEP_DATABASE_MIGRATION_PASSWORD",
            "UEP_DATABASE_RUNTIME_USERNAME",
        ))
        and "DatabaseMigrationRunner" in migration
    ),
    "runtime backend disables auto-Flyway after explicit migration": "set SPRING_FLYWAY_ENABLED=false" in launcher,
    "production-secret guard is opt-out only in non-production local mode": "set PLATFORM_DEV_MODE=true" in launcher,
    "local test identities carry a tenant claim and platform memberships": (
        "tenant_id" in launcher and "/api/v1/identity/bootstrap-owner" in launcher
        and "/api/v1/identity/memberships" in launcher
    ),
    "requirement inventory runs before installers and service startup": (
        "function Show-RequirementInventory" in launcher
        and "Show-RequirementInventory" in launcher
        and launcher.index("Show-RequirementInventory") < launcher.index("Ensure-Node")
        and "Frontend lockfile" in launcher
        and "Database migration runner" in launcher
        and "PostgreSQL service" in launcher
        and "SQLite local profile" in launcher
    ),
    "branch administrator test identity is attached only at branch scope": (
        "scopeKind = 'BRANCH'" in launcher
        and "scopeId = $testBranch.id" in launcher
        and "tenantScoped" in launcher
    ),
    "local tenant is marked local rather than as a hosted deployment": (
        "platform=local&status=active" in launcher
    ),
    "SQL bootstrap script is written without a UTF-8 BOM": (
        "$roleSql | Set-Content -LiteralPath $rolesFile -Encoding ASCII" in launcher
    ),
    "launcher displays backend, database, Keycloak and frontend health": (
        "Live service status" in launcher and all(token in launcher for token in (
            "Write-Status 'Database'", "Write-Status 'Backend'", "Write-Status 'Keycloak'", "Write-Status 'Frontend'"
        ))
    ),
    "process cleanup does not use unsupported Select-Object Reverse": (
        "[array]::Reverse($entries)" in launcher and "Select-Object -Reverse" not in launcher
    ),
    "temporary database passwords are not committed to the repository": (
        ".uep-local/" in (ROOT / ".gitignore").read_text(encoding="utf-8")
    ),
    "legacy root launcher delegates to the native launcher": (
        "scripts\\Start-UEP.ps1" in wrapper and "C:\\Users\\siddh" not in wrapper
    ),
    "backend Maven command retains explicit application main class": (
        "<mainClass>com.universalplatform.PlatformServerApplication</mainClass>" in pom
    ),
}

# Regression guards for defects found during native launcher source review.
checks.update({
    "Presentation SQLite provisions only the real OIDC owner, not unqualified backend personas": (
        "elseif ($Mode -eq 'Presentation' -and $Database -eq 'SQLite')" in launcher
        and "SKIPPED — the separate Offline Demo has its own seeded personas" in launcher
        and "Ensure-LocalTestAccounts $localAdminToken $adminToken" in launcher
    ),
    "SQLite launcher disables background workers in the actual Spring Boot process": (
        "set PLATFORM_BACKGROUND_WORKERS_ENABLED=false" in launcher
        and "-Dspring-boot.run.arguments=--platform.background-workers.enabled=false" in launcher
    ),
    "tenant bootstrap validates the API response identifier before saving local state": (
        "$tenant.PSObject.Properties['id']" in launcher
        and "Tenant bootstrap API response did not include the required id field" in launcher
    ),
    "bootstrap JWT requires tenant_id to be a scalar JSON string": (
        "$tenantClaim = $claims.PSObject.Properties['tenant_id']" in launcher
        and "$tenantClaim.Value -isnot [string]" in launcher
        and "must be a scalar JSON string" in launcher
    ),
    "API bootstrap failures identify the HTTP method and endpoint": (
        'UEP API $Method $Path failed (HTTP $statusCode)' in launcher
    ),
    "Keycloak tenant-attribute repair handles users with no attributes property": (
        "$user.PSObject.Properties['attributes']" in launcher
        and "$user.attributes.PSObject.Properties['tenant_id']" in launcher
        and "Add-Member -NotePropertyName attributes -NotePropertyValue ([pscustomobject]@{}) -Force" in launcher
    ),
    "launcher avoids shadowing PowerShell automatic HOME and args variables": (
        not re.search(r"(?im)^\s*\$(?:home|args)\s*=", launcher)
        and "$keycloakInstallDir = Join-Path $ToolDir 'keycloak-26.7.5'" in launcher
    ),
    "launcher defines each process/readiness helper exactly once": all(
        len(re.findall(rf"(?m)^function\s+{name}\s*\(", launcher)) == 1
        for name in ("Start-ManagedProcess", "Write-CommandFile", "Wait-ForHttp")
    ),
    "service readiness requires a successful 2xx response": (
        "$response.StatusCode -ge 200 -and $response.StatusCode -lt 300" in launcher
        and "$response.StatusCode -lt 500" not in launcher
    ),
    "PostgreSQL discovery selects supported majors numerically": (
        "major -notin @(17, 18)" in launcher
        and "Sort-Object -Property Major -Descending" in launcher
        and "Get-PostgresService" in launcher
        and "Sort-Object Name -Descending" not in launcher
    ),
    "credential-bearing local files receive explicit file ACLs": (
        "function Protect-File" in launcher
        and launcher.count("Protect-File") >= 6
        and "Protect-File $StateFile" in launcher
        and "Protect-File $rolesFile" in launcher
        and "Protect-File $accountsFile" in launcher
    ),
    "Windows presentation CI keeps services running through the SQLite browser smoke": (
        (launch_start := workflow.find("- name: Start the documented Real + Demo SQLite presentation launcher")) >= 0
        and (playwright_install := workflow.find("- name: Install Playwright Chromium", launch_start)) > launch_start
        and (browser_test := workflow.find("- name: Verify actual Windows OIDC-to-SQLite browser write/readback", playwright_install)) > playwright_install
        and (cleanup_start := workflow.find("- name: Ensure launcher processes are stopped", browser_test)) > browser_test
        and "finally {" not in workflow[launch_start:playwright_install]
        and "taskkill.exe" not in workflow[launch_start:cleanup_start]
        and "if: always()" in workflow[cleanup_start:]
    ),
})

failed = [name for name, passed in checks.items() if not passed]
for name, passed in checks.items():
    print(f"{'PASS' if passed else 'FAIL'}: {name}")
if failed:
    print(f"Native launcher contract failed ({len(failed)} checks).", file=sys.stderr)
    raise SystemExit(1)
print(f"Native launcher contract passed ({len(checks)} checks).")
