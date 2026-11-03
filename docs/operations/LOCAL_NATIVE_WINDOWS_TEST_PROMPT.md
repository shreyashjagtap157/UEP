# UEP — Full Native Local Real Frontend, API, Swagger UI and Database Acceptance Test Prompt (SQLite or PostgreSQL)

You are the principal QA automation engineer, full-stack integration tester, database verification engineer and application-security tester for the Universal Education Platform (UEP).

## Objective

Qualify the **actual Real application**, not the offline Demo fixtures. The Real browser UI, Spring Boot API, local Swagger UI and selected local database must operate as one connected system without starting or contacting Keycloak. Select SQLite for workstation qualification when PostgreSQL is unavailable; select PostgreSQL only when explicitly testing the PostgreSQL migration and privilege boundary.

The system is started natively on Windows by:

    .\startuep.ps1 -Mode Real -Database SQLite

Expected local endpoints:

- Frontend: http://localhost:5173
- Backend: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- SQLite (default): .uep-local/uep-local.sqlite. PostgreSQL is an optional separate mode.

In SQLite mode, the backend uses the `local-sqlite,local-native` Spring profiles and Hibernate entity schema generation; PostgreSQL Flyway migrations and database roles are not used. In PostgreSQL mode, the backend connects as `uep_local_runtime` and Flyway is run by `DatabaseMigrationRunner` as `uep_local_migrator`. The local-native profile binds the backend to `127.0.0.1`, enables launcher-issued local API authentication and exposes Swagger only on this workstation. No Keycloak process is started or contacted in either local database mode.

## Non-negotiable safety and truth requirements

1. Test only the local native environment. Do not access Fly.io, Supabase, Cloudflare APIs, remote production/staging endpoints or cloud databases.
2. Do not use Docker, Podman, Kubernetes, containers, paid agents, hosted runners or metered execution services.
3. Do not install additional infrastructure unless the owner explicitly approves. Use only the local tools provisioned by Start-UEP.ps1.
4. Never print, commit, upload or copy passwords, access tokens, client secrets, JDBC URLs with credentials, or the contents of state.json/accounts.txt.
5. Do not run DROP DATABASE, DROP SCHEMA, TRUNCATE, Flyway clean, baseline-on-migrate, restore-overwrite, or reset commands.
6. In PostgreSQL mode, do not use the postgres superuser or migration credential from the backend process; verify the API uses `uep_local_runtime`. In SQLite mode, verify the process uses the SQLite JDBC URL and local file only; no PostgreSQL credentials or process should be required.
7. All test-created records must use a clear test prefix such as NATIVE-E2E-<run-id>. Track identifiers and clean up only records created by this test, through authorized API operations. If a record cannot safely be deleted, document it instead of using direct destructive SQL.
8. Do not invent API paths or JSON request bodies. Read OpenAPI, controllers, DTOs, validation annotations, security rules and existing tests before exercising each feature.
9. Distinguish actual backend/API/database behavior from UI fixture behavior. Never pass a frontend-only demo interaction as a successful Real-mode test.
10. Every claim of success must cite a command, HTTP response, browser observation, database query or test result collected during this run. Do not infer a pass from code inspection alone.

## Phase 1 — Preflight and topology verification

Check all prerequisites and statuses:

- Node version >= 22.22.0 and npm available.
- JDK >= 21 and Maven >= 3.9.
- SQLite mode: the SQLite file is created by the backend and has a valid SQLite header. PostgreSQL mode: PostgreSQL accepts local connections.
- Swagger UI returns HTTP 200 at `/swagger-ui/index.html`; `/v3/api-docs` returns a valid OpenAPI document.
- Spring Boot actuator health is UP.
- Frontend page loads from port 5173.
- Required ports are bound by the expected local processes.

Record process IDs and log paths without exposing credentials.

Verify the data flow contract from source:

- Real frontend has `VITE_LOCAL_MODE=false` and `VITE_LOCAL_API_MODE=true`.
- Frontend does not compile in a remote API URL; the API base remains same-origin/relative.
- Vite proxies /api to 127.0.0.1:8080.
- Frontend API helper uses fetch for data requests, attaches the launcher-issued `X-UEP-Local-Auth` header and supports GET, POST, PUT, PATCH and DELETE.
- SQLite mode: backend uses `local-sqlite,local-native`, Flyway is disabled, Hibernate creates the local entity schema, background workers are disabled and no identity-provider process is started.
- PostgreSQL mode: backend uses the local PostgreSQL datasource and `uep_local_runtime`; Flyway migration history is present and validated; The local-native path does not start or contact Keycloak. Do not test or modify identity-provider schemas in this task.

Inspect browser and build artifacts for leaked secrets. No database username/password, PostgreSQL migration credential or launcher token may appear in frontend bundles, logs, screenshots or browser storage. The launcher token is supplied only to the Real Vite runtime and held in protected local process/command state.

## Phase 2 — Local API authentication and tenant context

1. Open the actual Real frontend at http://localhost:5173.
2. Confirm the Real UI stays at the local frontend and never redirects to port 8081.
3. Confirm a GET to /api/v1/me is sent through the Vite proxy with the X-UEP-Local-Auth header and without an Authorization bearer token.
4. Confirm /api/v1/me returns subject uep-local-demo-owner, an active tenant membership and the Organization Owner role.
5. Confirm the same request without the local header returns 401; an invalid local header also returns 401.
6. Verify the local authentication header is accepted only by the loopback-bound backend and only for /api routes.
7. Verify invalid/unknown tenant context and inactive memberships remain denied by the normal tenant and identity services.
8. Confirm the browser makes no request to port 8081 or any OIDC issuer.
9. Open the backend Swagger UI separately and confirm its OpenAPI JSON loads without an API token.

## Phase 3 — Browser-to-API request proof

Use browser automation and browser network instrumentation.

Start with the committed native Real smoke suite after the launcher reports all four local services ready:

    npm --prefix apps/web exec -- playwright install chromium
    npm --prefix apps/web run test:e2e:native

This test must run on the same Windows account that initialized the protected local state. It checks local-authenticated API calls from the Vite origin, downloads the attendance CSV, creates a uniquely prefixed branch through the UI, reloads the browser and reads the branch back. It then creates a synthetic role-free membership through the People UI, assigns the existing Student role at tenant scope through authenticated POST, verifies the `role_assignment` row exists through the restricted PostgreSQL runtime role, removes that assignment through authenticated DELETE, verifies the row is absent, repeats the assignment/removal at branch scope against the newly created branch and verifies the persisted scope kind and branch ID, updates the membership's status and external reference through authenticated PATCH with optimistic version 0, verifies the response increments the version to 1, reloads and reads the updated membership through GET, and directly reads the committed membership row from PostgreSQL using the protected `uep_local_runtime` credentials. It then creates a uniquely prefixed custom role in the Roles workspace, updates its name/description/permissions through PUT using optimistic version 0, verifies version 1 in the response and PostgreSQL, deletes it through the UI after explicit browser confirmation, and verifies that the role and cascading permission rows are absent through the restricted runtime role. System-managed roles have no edit/delete controls, and the backend must reject deletion of an assigned custom role. It also verifies the database role's no-CREATE/no-Flyway-history boundary. It deliberately does not print credentials or tokens. The uniquely named branch and synthetic membership are annotated and retained because the current UI/API has no delete operation for either resource; the test never deletes data with direct SQL.

A successful run is evidence for this specific end-to-end path only; continue with the wider method, workflow and tenant/branch isolation matrix below.

For every major visible data area available to the authenticated owner/admin:

- Record the UI action.
- Record the exact HTTP method and local /api path.
- Record request status and response shape (redact PII/secrets).
- Confirm the request went to the Vite origin and was proxied to the local API.
- Confirm the API actually read or changed PostgreSQL state.
- Confirm the UI rendered the returned server data, not a local fixture or a hard-coded fallback.

Cover all supported method classes:
- GET: list, details, current identity, settings, dashboard and health/read-only resources.
- POST: create resources, assignments, submissions and actions that create records.
- PUT: full updates where the endpoint defines them.
- PATCH: update membership status, primary branch and tenant-local external reference from the People workspace. Use a non-owner test membership, preserve its current branch when branch selection is unavailable, verify optimistic `expectedVersion` handling, and confirm the final-active-owner invariant remains enforced.
- DELETE: authorized removal/revocation operations where the endpoint defines them.

Do not create fake tests to satisfy method coverage. Discover actual operations from docs/api/openapi.yaml and Spring controllers. If the product currently has no real frontend method for an implemented API operation, record that as a UI integration gap.

Inspect browser console and network logs. There must be no:
- CORS failures,
- localhost IPv6/proxy connection failures,
- 404/405 caused by a wrong frontend route,
- unexpected 401/403 for an authorized local persona,
- swallowed network exception presented as successful saved data,
- calls from the browser directly to PostgreSQL,
- calls to Fly, Supabase or any remote backend.

## Phase 4 — End-to-end persisted business workflows

Use the real frontend and the seeded local personas. Inspect the current API contracts first and then execute the workflows that are implemented.

At minimum, qualify this lifecycle if the repository exposes the corresponding APIs and UI:

1. Owner/admin creates or verifies a local organization, branch, academic period, program/course/batch and required memberships.
2. Owner/admin assigns a teacher and a student to the correct academic scope.
3. Teacher sees only assigned teaching work and the enrolled learners.
4. Teacher creates and publishes an assignment or assessment.
5. Student sees the assignment/assessment in the student workspace and submits a response.
6. Teacher/evaluator sees that submission only within their authorized scope, grades it and publishes a result.
7. Student reloads the page and sees the server-persisted result and feedback.
8. Attendance is recorded through the authorized workflow and appears in the correct learner/teacher views.
9. An announcement is created, targeted to the appropriate branch/course/audience, published or scheduled according to the supported workflow, and visible only to its intended audience.
10. Notification/outbox effects and state transitions are verified in the database or corresponding authorized operations view.

For areas not currently available through the UI, do not fake completion. Report the absent UI integration and separately test the API only when an existing documented endpoint supports it.

## Phase 5 — PostgreSQL database persistence and least-privilege proof (PostgreSQL mode only)

Run this phase only in PostgreSQL mode. In SQLite mode, do not invoke psql or run PostgreSQL role/grant assertions; use the dedicated SQLite native Playwright test and verify the file signature plus API readback instead. Use psql or an approved local database client only for PostgreSQL mode. Do not display passwords.

Verify:

- current database is uep_local_native.
- backend application tables exist in public.
- Flyway schema history contains the expected successfully applied versions.
- The local-native runtime does not connect to an identity-provider database.
- runtime account is a login with no superuser, CREATEDB, CREATEROLE, REPLICATION, BYPASSRLS, role membership, schema CREATE, database CREATE or application-object ownership.
- No external identity-provider database is connected by the local-native application path.
- runtime account cannot read Flyway history or create schema objects.
- app data rows created through the frontend/API are present in the expected PostgreSQL tables.
- updates and deletes through the frontend/API are reflected in PostgreSQL.
- after closing and reopening the browser, the state is still returned by API reads from PostgreSQL.

Do not assume table or column names. Derive them from the tracked migrations and JPA mappings before querying. Use parameterized SQL or read-only SELECT statements for verification.

For at least one complete create/update/read/delete cycle, preserve evidence of:
1. Browser action.
2. Corresponding API request/response.
3. Database row before/after.
4. UI after refresh.

## Phase 6 — Tenant and role isolation

Use at least two distinct local test identities/tenants where the supported model allows them.

Test:
- Student cannot read another student's records.
- Teacher cannot read students/classes outside assigned scope.
- Guardian cannot access an unlinked learner.
- Organization/client admin cannot access another tenant.
- Support/auditor read-only personas cannot perform prohibited mutations.
- Branch-scoped role access does not expand to tenant-wide access.
- Tenant identifiers from request headers/body cannot override the signed JWT tenant_id.
- Revoked or ended memberships lose API access.
- Cross-tenant foreign keys and references are rejected by PostgreSQL constraints.

Perform negative tests with known test records and verify 401/403/404 behavior is consistent with the API's deliberate disclosure policy. Do not interpret a 404 by itself as proof of isolation; cross-check database ownership and authorization code.

## Phase 7 — Automated checks

Run and capture results for:

Frontend:
    npm --prefix apps/web ci --ignore-scripts
    npm --prefix apps/web run lint
    npm --prefix apps/web test
    npm --prefix apps/web run build

Backend:
    mvn -B -f apps/platform-server/pom.xml verify

Repository policy:
    bash scripts/verify-repository.sh
    python3 scripts/verify-local-native-launcher.py

Browser:
- For the native Real environment, install Chromium once when absent and run:
      npm --prefix apps/web exec -- playwright install chromium
      npm --prefix apps/web run test:e2e:native
- The native smoke reads the restricted PostgreSQL runtime credentials only from the protected LocalAppData state file, keeps them in process memory, passes the database password to `psql` through the child-process environment, and disables trace/screenshot/video output. It verifies launcher-issued loopback authentication, authenticated same-origin `/api/v1/me`, analytics overview, an authenticated attendance CSV export, a branch POST from the Organization UI, browser reload followed by API readback, and direct PostgreSQL readback of that branch as `uep_local_runtime`.
- It also verifies that the connected database role is the configured restricted runtime user, has no superuser/role/database-creation/replication/RLS-bypass attributes, lacks schema and database CREATE privileges, and is denied access to `public.flyway_schema_history`. It deliberately retains the uniquely named test branch because the branch API currently exposes no delete operation; the test records its code and ID in a Playwright annotation and never deletes data with direct SQL.
- Treat this as focused end-to-end evidence for one local owner, analytics export, branch-create/read, membership-create/role-assign/role-unassign/PATCH/read, optimistic-version increment, and direct PostgreSQL readback. It does **not** prove general PUT/DELETE coverage outside the custom-role update/delete path and the specific operations above, all UI workflows, multi-persona or cross-tenant/branch isolation across modules, complete object-ownership/grant analysis, or service/database restart persistence. Those remain separate mandatory phases below.
- Discover and run the existing Playwright suite.
- Add focused Real-mode integration tests only where the current setup supports them.
- Keep the Demo suite separate; it does not count as Real backend/database qualification.

If an existing test requires an external service, skip it only with an explicit reason and mark that capability unverified. Do not disable or weaken an assertion to obtain a green run.

## Phase 8 — Restart and persistence test

1. Create a uniquely named test resource through the real UI.
2. Confirm the API accepted it and PostgreSQL stores it.
3. Stop only the frontend and backend processes (leave PostgreSQL running).
4. Restart the frontend and backend using the local launcher.
5. Sign in again and confirm the resource is still returned by the API and visible in the UI.
6. Restart the backend and frontend only after the previous check; confirm the new local session works and the same database records remain.
7. Do not reset the database.

## Required final report

Produce a factual evidence report with these sections:

1. Environment and tool versions.
2. Service endpoints and live status.
3. Verified browser → Vite proxy → API → PostgreSQL request path.
4. Verified launcher-issued local API session and tenant context.
5. API method coverage table (GET/POST/PUT/PATCH/DELETE).
6. UI feature/workflow coverage table.
7. Database migrations, tables, role grants and persistence proof.
8. Tenant/role isolation results.
9. Frontend lint/test/build, Maven verify and Playwright results.
10. Failures, root causes and remaining gaps.
11. Exact log paths and redacted evidence references.

Label every item PASS, FAIL, BLOCKED or NOT TESTED. Separate verified facts from inferred behavior. Do not claim that all features work just because the home dashboard loads or actuator health is UP.


## SQLite local qualification

Use the SQLite profile when PostgreSQL is unavailable. The launcher accepts a SQLite database selection; the local qualification database is a file under `.uep-local`. The profile uses Hibernate entity schema generation, not the PostgreSQL Flyway migrations. The local-native profile does not need an identity-provider service.

The SQLite native test validates local API authentication, authenticated branch creation, browser reload/readback and the SQLite file signature. PostgreSQL role/grant tests remain a separate PostgreSQL-only acceptance suite. SQLite results must not be represented as PostgreSQL migration, privilege, concurrency or production-equivalence evidence.


## SQLite local qualification mode — use this path when PostgreSQL is unavailable

Use SQLite when PostgreSQL is unavailable. From the repository root:

```powershell
.\startuep.ps1 -Mode Real -Database SQLite
```

SQLite is the default local qualification database. PostgreSQL remains an explicit alternative:

```powershell
.\startuep.ps1 -Mode Real -Database PostgreSQL
```

SQLite mode does not install or start PostgreSQL and does not execute Flyway's PostgreSQL migrations. Hibernate generates the local qualification schema from the JPA entity model in `.uep-local\uep-local.sqlite`. The local-native profile does not start or contact an external identity provider. Runtime workers that depend on PostgreSQL advisory locks are disabled.

After the launcher reports the backend healthy, run:

```powershell
Set-Location apps\web
npm run test:e2e:native
```

The SQLite native test validates local API authentication, authenticated branch creation, browser reload/readback and the SQLite file signature. PostgreSQL role/grant checks are a separate suite and are skipped in SQLite mode. This is a narrow local workflow qualification, not proof that every UEP module supports SQLite. Do not count membership lifecycle, role administration, analytics, scheduling, messaging, finance, notification delivery, or background-worker workflows as SQLite-qualified until their PostgreSQL-specific query and locking paths have dedicated SQLite tests. SQLite does not establish PostgreSQL migration, role/grant, concurrency, backup/restore or production-equivalence qualification.


## Paired presentation environment

Start Real UEP and the separate offline demo together:

```powershell
.\startuep.ps1 -Mode Presentation -Database SQLite
```

Real UEP runs at `http://localhost:5173`; the offline seeded demo runs at `http://localhost:5174`. Both use separate Vite process environments and visible environment labels. Only Real uses Spring Boot and the selected database. It uses the launcher-issued local API session; no Keycloak service is started. Demo remains browser-fixture-only.

Use `docs/presentation/UEP_PRESENTATION_RUNBOOK.md` for the walkthrough and evidence boundaries.
