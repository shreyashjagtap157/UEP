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

launcher = launcher_path.read_text(encoding="utf-8")
wrapper = wrapper_path.read_text(encoding="utf-8")
vite = vite_path.read_text(encoding="utf-8")
pom = pom_path.read_text(encoding="utf-8")
migration = migration_path.read_text(encoding="utf-8")

checks = {
    "launcher supports Demo, Real and Both modes": "ValidateSet('Demo', 'Real', 'Both')" in launcher,
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
    "Both mode isolates offline Demo on a separate port": (
        "5174" in launcher and "set VITE_LOCAL_MODE=true" in launcher
    ),
    "PostgreSQL is provisioned without a container engine": (
        "PostgreSQL 18.4" in launcher and "pg_isready.exe" in launcher
        and not re.search(r"\b(?:docker|podman)\s+(?:run|compose|build)\b", launcher, re.I)
    ),
    "Keycloak is run from the native distribution": (
        "keycloak-26.7.0.zip" in launcher and "kc.bat" in launcher
        and "start-dev --http-port=8081 --import-realm" in launcher
    ),
    "Maven installation verifies the downloaded SHA-512": (
        "apache-maven-3.10.0-bin.zip" in launcher and "Get-FileHash" in launcher
        and "SHA512" in launcher
    ),
    "Keycloak archive verifies the pinned SHA-256": (
        "e63bd0167199c0092b8a4d22cc137e6b7a70e0089070f6f7799b1be504b69a8a" in launcher
        and "Algorithm SHA256" in launcher
    ),
    "local database uses distinct migrator, runtime and Keycloak roles": (
        all(name in launcher for name in (
            "uep_local_migrator", "uep_local_runtime", "uep_local_keycloak"
        ))
    ),
    "Keycloak owns a separate schema": "KC_DB_SCHEMA=keycloak" in launcher
        and "CREATE SCHEMA keycloak AUTHORIZATION " in launcher,
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
    "launcher defines each process/readiness helper exactly once": all(
        len(re.findall(rf"(?m)^function\\s+{name}\\s*\\(", launcher)) == 1
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
})

failed = [name for name, passed in checks.items() if not passed]
for name, passed in checks.items():
    print(f"{'PASS' if passed else 'FAIL'}: {name}")
if failed:
    print(f"Native launcher contract failed ({len(failed)} checks).", file=sys.stderr)
    raise SystemExit(1)
print(f"Native launcher contract passed ({len(checks)} checks).")
