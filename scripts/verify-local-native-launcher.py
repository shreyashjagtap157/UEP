#!/usr/bin/env python3
"""Static contract checks for the native Windows UEP launcher.

This does not execute Windows services. CI separately parses the PowerShell source;
the real Windows acceptance run must still be performed on a developer workstation.
"""
from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
launcher_path = ROOT / "scripts" / "Start-UEP.ps1"
wrapper_path = ROOT / "startuep.ps1"
vite_path = ROOT / "apps" / "web" / "vite.config.ts"
pom_path = ROOT / "apps" / "platform-server" / "pom.xml"
migration_path = ROOT / "apps" / "platform-server" / "src" / "main" / "java" / "com" / "universalplatform" / "DatabaseMigrationRunner.java"
sqlite_profile_path = ROOT / "apps" / "platform-server" / "src" / "main" / "resources" / "application-local-sqlite.yaml"
local_native_profile_path = ROOT / "apps" / "platform-server" / "src" / "main" / "resources" / "application-local-native.yaml"
local_native_profile = local_native_profile_path.read_text(encoding="utf-8")
workflow_path = ROOT / ".github" / "workflows" / "ci.yml"

launcher = launcher_path.read_text(encoding="utf-8")
wrapper = wrapper_path.read_text(encoding="utf-8")
vite = vite_path.read_text(encoding="utf-8")
pom = pom_path.read_text(encoding="utf-8")
migration = migration_path.read_text(encoding="utf-8")
sqlite_profile = sqlite_profile_path.read_text(encoding="utf-8")
sqlite_test = (ROOT / "apps" / "platform-server" / "src" / "test" / "java" / "com" / "universalplatform" / "tenancy" / "SQLiteLocalQualificationIntegrationTest.java").read_text(encoding="utf-8")
scheduled_worker_paths = [
    "apps/platform-server/src/main/java/com/universalplatform/communication/AnnouncementPublishingWorker.java",
    "apps/platform-server/src/main/java/com/universalplatform/content/ContentRetentionWorker.java",
    "apps/platform-server/src/main/java/com/universalplatform/content/StorageCleanupWorker.java",
    "apps/platform-server/src/main/java/com/universalplatform/content/UploadCleanupWorker.java",
    "apps/platform-server/src/main/java/com/universalplatform/integration/WebhookEventFanoutWorker.java",
    "apps/platform-server/src/main/java/com/universalplatform/integration/WebhookWorker.java",
    "apps/platform-server/src/main/java/com/universalplatform/messaging/MessagingRetentionWorker.java",
    "apps/platform-server/src/main/java/com/universalplatform/notification/NotificationEmailWorker.java",
    "apps/platform-server/src/main/java/com/universalplatform/notification/NotificationOutboxProcessor.java",
    "apps/platform-server/src/main/java/com/universalplatform/recording/RecordingWorker.java",
    "apps/platform-server/src/main/java/com/universalplatform/storage/StorageQuotaReconciliationWorker.java",
]
workflow = workflow_path.read_text(encoding="utf-8")
class_version_verifier = (ROOT / "scripts" / "verify-java-class-version.ps1").read_text(encoding="utf-8")

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
    "local-native profile binds the real API to loopback": (
        "address: 127.0.0.1" in local_native_profile
        and "local-auth:\n    enabled: true" in local_native_profile
    ),
    "Demo mode enables local fixture authentication": "set VITE_LOCAL_MODE=true" in launcher,
    "Real mode disables local fixture authentication": "set VITE_LOCAL_MODE=false" in launcher,
    "Real mode uses the launcher-issued local API header instead of OIDC": (
        "set VITE_LOCAL_API_MODE=true" in launcher
        and "VITE_LOCAL_API_AUTH_TOKEN=" in launcher
        and "X-UEP-Local-Auth" in (ROOT / "apps" / "web" / "src" / "platform" / "api-core.ts").read_text(encoding="utf-8")
        and "VITE_KEYCLOAK_URL=http://localhost:8081" not in launcher
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
    "Real launcher does not install, start or probe Keycloak": (
        "Ensure-Keycloak\n        Test-PortAvailable 8080" not in launcher
        and "Test-PortAvailable 8081" not in launcher
        and "Start-ManagedProcess 'Keycloak'" not in launcher
        and "Wait-ForHttp 'http://127.0.0.1:8081" not in launcher
    ),
    "Maven installation verifies the downloaded SHA-512": (
        "apache-maven-3.10.0-bin.zip" in launcher and "Get-FileHash" in launcher
        and "SHA512" in launcher
    ),
    "PostgreSQL local schema has separate migration/runtime roles": all(name in launcher for name in ("uep_local_migrator", "uep_local_runtime")),
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
    "the local owner membership and Organization Owner role are persisted by backend bootstrap": (
        "uep-local-demo-owner" in (ROOT / "apps" / "platform-server" / "src" / "main" / "java" / "com" / "universalplatform" / "identity" / "LocalDevelopmentIdentityBootstrapper.java").read_text(encoding="utf-8")
        and "SystemRoleKey.ORGANIZATION_OWNER" in (ROOT / "apps" / "platform-server" / "src" / "main" / "java" / "com" / "universalplatform" / "identity" / "LocalDevelopmentIdentityBootstrapper.java").read_text(encoding="utf-8")
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
    "launcher displays backend, database, local identity and frontend health": (
        "Live service status" in launcher and all(token in launcher for token in (
            "Write-Status 'Database'", "Write-Status 'Backend'", "Write-Status 'Identity'", "Write-Status 'Frontend'"
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
    "Swagger UI and OpenAPI are local-native only and covered by integration tests": (
        "springdoc-openapi-starter-webmvc-ui" in pom
        and "PLATFORM_API_DOCS_ENABLED:false" in (ROOT / "apps" / "platform-server" / "src" / "main" / "resources" / "application.yaml").read_text(encoding="utf-8")
        and "swagger-ui/index.html" in launcher
        and "v3/api-docs" in launcher
        and "localSwaggerUiAndOpenApiDescriptionAreAvailableWithoutAnApiSession" in (ROOT / "apps" / "platform-server" / "src" / "test" / "java" / "com" / "universalplatform" / "security" / "LocalDevelopmentAuthenticationIntegrationTest.java").read_text(encoding="utf-8")
    ),
    "local auth is loopback-only, bound to localhost, and restricted to API routes": (
        "isLoopback(request.getRemoteAddr())" in (ROOT / "apps" / "platform-server" / "src" / "main" / "java" / "com" / "universalplatform" / "security" / "LocalDevelopmentAuthenticationFilter.java").read_text(encoding="utf-8")
        and "getRequestURI().startsWith(\"/api/\")" in (ROOT / "apps" / "platform-server" / "src" / "main" / "java" / "com" / "universalplatform" / "security" / "LocalDevelopmentAuthenticationFilter.java").read_text(encoding="utf-8")
        and "address: 127.0.0.1" in local_native_profile
    ),
    "local-native does not attempt external OIDC issuer discovery": (
        "@Profile(\"local-native\")" in (ROOT / "apps" / "platform-server" / "src" / "main" / "java" / "com" / "universalplatform" / "security" / "LocalNativeJwtDecoderConfiguration.java").read_text(encoding="utf-8")
        and "OIDC bearer tokens are disabled" in (ROOT / "apps" / "platform-server" / "src" / "main" / "java" / "com" / "universalplatform" / "security" / "LocalNativeJwtDecoderConfiguration.java").read_text(encoding="utf-8")
    ),
    "Real launcher verifies the Vite API proxy and actual persisted identity": (
        "set SPRING_PROFILES_ACTIVE=local-sqlite,local-native" in launcher
        and "set VITE_LOCAL_API_MODE=true" in launcher
        and "Wait-ForHttp 'http://127.0.0.1:5173/api/v1/platform/version'" in launcher
        and "Test-LocalApiProxy" in launcher
        and "UEP_LOCAL_AUTH_TOKEN=" in launcher
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
    "launcher avoids shadowing PowerShell automatic HOME and args variables": (
        not re.search(r"(?im)^\s*\$(?:home|args)\s*=", launcher)
        and "$RepoRoot = (Resolve-Path" in launcher
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
    "launcher-issued tokens are written only to ACL-protected command files": (
        "function Protect-File" in launcher
        and "Protect-File $StateFile" in launcher
        and "function Write-CommandFile" in launcher
        and "Protect-File $path" in launcher
    ),
    "Windows setup requires a full JDK and supports dependency upgrades/fallbacks": (
        "javac.exe" in launcher
        and "EclipseAdoptium.Temurin.21.JDK" in launcher
        and "nodejs.org/en/download" in launcher
        and "winget.exe upgrade --id 'OpenJS.NodeJS.LTS'" in launcher
        and "System Maven is too old or invalid" in launcher
        and "Maven 3.9+ could not be located" in launcher
    ),
    "all scheduled background worker beans are gated by the local worker opt-in": all(
        '@ConditionalOnProperty(prefix = "platform.background-workers", name = "enabled", havingValue = "true", matchIfMissing = true)' in
        (ROOT / worker_path).read_text(encoding="utf-8")
        for worker_path in scheduled_worker_paths
    ),
    "database provider changes or a missing SQLite file trigger non-destructive tenant rebootstrap": (
        "$providerChanged = -not [string]::Equals($previousProvider, $Database" in launcher
        and "$sqliteIdentityMustBeRecreated = ($Database -eq 'SQLite' -and $sqliteMissingOrEmpty)" in launcher
        and "$State.TenantId = ''" in launcher
        and "$State.LocalAdminUserId = ''" in launcher
        and "preserving local credentials and database files" in launcher
    ),
    "missing JDK is rejected before running java or javac diagnostics": (
        launcher.find("if (-not $java) { throw 'A full JDK 21 or later") >= 0
        and launcher.find("if (-not $java) { throw 'A full JDK 21 or later") < launcher.find("$javaRuntimeText = (& $java -version")
        and launcher.find("if (-not $java) { throw 'A full JDK 21 or later") < launcher.find("$javaCompilerText = (& $javaCompiler -version")
    ),
    "Windows backend launch pins matching JDK tools and performs a clean Java 21 compile": (
        "set \"JAVA_HOME=" in launcher
        and "set \"PATH=" in launcher
        and "java -version" in launcher
        and "javac -version" in launcher
        and "'-version')" in launcher
        and "-Dmaven.compiler.release=21 clean compile" in launcher
        and "verify-java-class-version.ps1" in launcher
        and "-ExpectedMajor 65" in launcher
        and "-Dmaven.compiler.release=21 spring-boot:run" in launcher
        and "<maven.compiler.release>21</maven.compiler.release>" in pom
    ),
    "Java class-version verifier validates grading package-info and all generated classes": (
        "com\\universalplatform\\grading\\package-info.class" in class_version_verifier
        and "Get-ClassMajorVersion" in class_version_verifier
        and "$major -gt $ExpectedMajor" in class_version_verifier
        and "all {0} backend class files are compatible" in class_version_verifier
    ),
    "SQLite schema update uses individually extracted Hibernate metadata instead of grouped compound SELECT": (
        "jdbc_metadata_extraction_strategy: individually" in sqlite_profile
        and "hibernate.hbm2ddl.jdbc_metadata_extraction_strategy" in sqlite_test
        and "existingSqliteSchemaSupportsPerTableColumnMetadataExtraction" in sqlite_test
        and "existingSqliteDatabaseCanStartASecondRealApplicationContext" in sqlite_test
        and "new SpringApplicationBuilder(PlatformServerApplication.class)" in sqlite_test
        and '"spring.jpa.hibernate.ddl-auto"' in sqlite_test
        and '.isEqualTo("update")' in sqlite_test
        and "metadata.getColumns(null, null, tableName, null)" in sqlite_test
    ),
    "SQLite backend integration test asserts scheduler and worker suppression": (
        "localSqliteProfileDisablesSchedulingAndEveryBackgroundWorker" in sqlite_test
        and "storageQuotaReconciliationWorker" in sqlite_test
        and '"platform.background-workers.enabled=false"' in sqlite_test
    ),
    "Windows SQLite presentation runs Playwright before process cleanup in the same step": (
        (launch_start := workflow.find("- name: Start Real + Demo SQLite and run the native local-auth-to-SQLite browser test")) >= 0
        and (cleanup_start := workflow.find("- name: Ensure launcher processes are stopped", launch_start)) > launch_start
        and (playwright_install := workflow.find("npm --prefix apps/web exec -- playwright install chromium", launch_start)) > launch_start
        and (browser_test := workflow.find("npm --prefix apps/web run test:e2e:native", playwright_install)) > playwright_install
        and browser_test < cleanup_start
        and "finally {" in workflow[launch_start:cleanup_start]
        and "taskkill.exe /PID $launcher.Id /T /F" in workflow[launch_start:cleanup_start]
        and "if: always()" in workflow[cleanup_start:]
        and "- name: Install Playwright Chromium" not in workflow
        and "- name: Verify actual Windows OIDC-to-SQLite browser write/readback" not in workflow
    ),
})

failed = [name for name, passed in checks.items() if not passed]
for name, passed in checks.items():
    print(f"{'PASS' if passed else 'FAIL'}: {name}")
if failed:
    print(f"Native launcher contract failed ({len(failed)} checks).", file=sys.stderr)
    raise SystemExit(1)
print(f"Native launcher contract passed ({len(checks)} checks).")
