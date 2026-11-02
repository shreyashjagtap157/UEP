# Native Windows local development

## Purpose

This is the supported local fallback when hosted API, identity and database infrastructure is unavailable. It runs the web client, Spring Boot API, Keycloak and PostgreSQL as native Windows processes and services. **It does not use Docker, Podman, Kubernetes, containers, Fly.io, Supabase, a cloud database, or a paid execution service.**

The root-level startuep.ps1 is a compatibility wrapper. The implementation is scripts/Start-UEP.ps1.

## Modes

| Mode | Frontend | Backend | Identity | Database | Data boundary |
|---|---|---|---|---|---|
| Demo | http://localhost:5173 | Not started | Not started | Not started | Offline UI fixtures only |
| Real | http://localhost:5173 | http://localhost:8080 | http://localhost:8081 | PostgreSQL uep_local_native on 127.0.0.1:5432 | Real API + persisted local PostgreSQL data |
| Both | Real: http://localhost:5173; Demo: http://localhost:5174 | http://localhost:8080 | http://localhost:8081 | PostgreSQL uep_local_native on 127.0.0.1:5432 | Separate real and offline demo experiences |

The Demo mode deliberately remains offline. It does not call the API, Keycloak or PostgreSQL and must not be described as a real-data or persistence demo. In Both mode, the Real and Demo Vite servers use separate ports and separate Vite process environments.

## Requirements and first-run behavior

The launcher checks for the required toolchain before starting application processes:

- Windows PowerShell 5.1 or PowerShell 7, x64 Windows.
- JDK 21 or later.
- Node.js 22.22.0 or later and npm.
- Apache Maven 3.9 or later.
- PostgreSQL 17 or 18 with psql and pg_isready; the launcher offers the PostgreSQL 18.4 EDB installer through WinGet when missing.
- Keycloak 26.7.5; the launcher downloads the official native ZIP and verifies its pinned SHA-256 before extracting it.

When available, WinGet installs missing JDK, Node.js and PostgreSQL prerequisites. The PostgreSQL installer opens interactively so the owner can set a local postgres superuser password. Maven and Keycloak are downloaded into the ignored .uep-local/tools directory. Apache Maven's published SHA-512 and the Keycloak release SHA-256 are checked before extraction.

If Windows requires elevation to start the PostgreSQL service, open PowerShell as Administrator and rerun the launcher. The launcher does not alter an already-running PostgreSQL service. It starts the service only if it is stopped and will stop it on exit only when the launcher itself started it.

Ports 5173, 5174, 8080, 8081 and 5432 must be available for the selected mode. Demo mode only requires port 5173.

## Start it

Run these commands from the repository root:

    # Recommended: start the real application and offline demo side by side.
    .\scripts\Start-UEP.ps1 -Mode Both

    # Start only the real application.
    .\scripts\Start-UEP.ps1 -Mode Real

    # Start only the offline UI demo.
    .\scripts\Start-UEP.ps1 -Mode Demo

    # Do not automatically open browser windows.
    .\scripts\Start-UEP.ps1 -Mode Both -NoBrowser

    # Start Real without provisioning the additional role personas.
    .\scripts\Start-UEP.ps1 -Mode Real -SkipAccountSeeding

The older root-level entry point also works:

    .\startuep.ps1 -Mode Both

If script execution policy blocks a local script, use a process-scoped bypass rather than changing the machine policy:

    powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Start-UEP.ps1 -Mode Both

Press Ctrl+C in the launcher window to stop application processes. It preserves local database data, local accounts, logs and downloaded tools. It does not drop, clean, reset or truncate the database.

## Native Real browser smoke test

After starting the environment with `.\scripts\Start-UEP.ps1 -Mode Real -NoBrowser`, open a second PowerShell window in the repository root. Install the Playwright Chromium browser once if it is not already installed, then run the dedicated native Real smoke test:

    npm --prefix apps/web exec -- playwright install chromium
    npm --prefix apps/web run test:e2e:native

The test reads only the owner username/password from the protected current-user LocalAppData state file into process memory; it never prints, copies, attaches or persists credentials or tokens. Traces, screenshots and video are disabled. Its current coverage is limited to local Keycloak owner sign-in, the authenticated `/api/v1/me` and analytics overview requests, and a bearer-authenticated attendance CSV download through the Vite origin.

This test is an initial Real-mode browser integration smoke, not a substitute for the full workflow, database-write/readback, tenant-isolation, or restart-persistence acceptance plan. A pass must not be reported as proof of those broader capabilities.

## Real-mode request and data path

The Real web app is built with VITE_LOCAL_MODE=false, an empty VITE_BACKEND_URL, and the repository's Keycloak realm/client configuration. API calls use same-origin relative /api/... paths. Vite proxies those calls to the API on IPv4 loopback:

    Browser
      |
      | http://localhost:5173
      v
    Vite / React
      |
      | fetch("/api/v1/...")
      | Vite proxy -> http://127.0.0.1:8080
      v
    Spring Boot API
      |
      | JWT validation / tenant context / authorization
      | JDBC as uep_local_runtime
      v
    PostgreSQL 127.0.0.1:5432 / uep_local_native

Authentication is a separate local path:

    React -> Keycloak 26.7.5 at http://localhost:8081
           -> realm education
           -> public browser client platform-web
           -> Authorization Code + PKCE S256
           -> signed access token with tenant_id claim
           -> Spring Boot API validates issuer/JWKS and tenant context

The browser never connects directly to PostgreSQL and never receives database credentials. Keycloak and the API use separate PostgreSQL users and schemas.

## Database ownership and migration lifecycle

The local database is uep_local_native, with these identities:

| Identity | Responsibility | Boundary |
|---|---|---|
| uep_local_migrator | One-shot Flyway migrations | Owns application schema objects; never used by request-serving API |
| uep_local_runtime | Spring Boot API | Standalone least-privilege login; no schema/database creation, role membership, superuser or RLS bypass |
| uep_local_keycloak | Keycloak persistence | Owns only schema keycloak; separate from UEP public application data |

On first initialization the launcher creates these local roles and the keycloak schema. It then runs Maven's `compile` phase before invoking the repository's `DatabaseMigrationRunner` with the required UEP_DATABASE_MIGRATION_* variables. This is required on a clean checkout because the runner is a Java main class in the project source tree. Flyway validates/applies the tracked migrations and grants the application runtime role its intended privileges.

After this one-shot migration finishes, Spring Boot starts with SPRING_FLYWAY_ENABLED=false and connects only as uep_local_runtime. Keycloak connects as uep_local_keycloak with KC_DB_SCHEMA=keycloak. The launcher never points the local app at Supabase or any other remote database.

## Local accounts and secrets

Generated local credentials and connection secrets are stored beneath:

    %LOCALAPPDATA%\UEP\local-native\

The launcher restricts that directory's Windows ACL to the current user. state.json stores generated local service credentials and bootstrap state; accounts.txt contains the local test persona credentials. Keep these files private and never commit or upload them. The folder is outside the repository and the .uep-local/ runtime/tool/log directory is git-ignored.

Real mode provisions a local platform super-administrator, one local test tenant and role-scoped OIDC identities for the platform's supported personas. The additional personas can be skipped with -SkipAccountSeeding; the initial local admin bootstrap remains necessary for the real identity path.

## Runtime status and logs

While running, the launcher periodically displays:

- PostgreSQL connection status using the local runtime database role.
- Keycloak TCP listener status.
- Spring Boot /actuator/health status.
- Real and/or Demo Vite listener status.

Logs and generated command files are kept under .uep-local\logs and .uep-local. Inspect those files if a readiness check times out. Error output is emitted when a dependency or process fails to start.

## Verification boundary

A successful launcher startup proves that its configured local services responded to their readiness checks. It does **not** prove every UEP domain workflow, role/tenant isolation rule, or every API operation is correct. Run the acceptance plan in LOCAL_NATIVE_WINDOWS_TEST_PROMPT.md after startup.

The launcher itself is statically checked by scripts/verify-local-native-launcher.py and parsed by PowerShell in CI. Windows runtime qualification still needs to be performed on a Windows workstation; CI cannot claim a local Windows launch on the owner's machine.

## Recovery and safety

- Do not set a production Spring profile for this local development launcher.
- Do not enable automatic Flyway on the request-serving runtime role.
- Do not replace local uep_local_runtime with postgres or the migration role.
- Do not point the local API or Keycloak at the paused hosted Supabase project.
- Do not drop or reset any database to repair a startup issue. Inspect the error, migration history and role grants first.
- To stop the environment, press Ctrl+C. Local database contents are preserved.
