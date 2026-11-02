# Next-session handoff — UEP native Windows full-stack qualification

Use this prompt as the first message in a new ChatGPT conversation.

---

## Mission

Continue engineering and qualify the Universal Education Platform repository:

https://github.com/shreyashjagtap157/UEP

Act as the principal engineer, Windows PowerShell engineer, Spring Boot/API engineer, PostgreSQL engineer, OIDC/Keycloak engineer, frontend integration engineer, security tester and release verifier.

The immediate goal is to get the **actual Real UEP application** running locally on Windows without Docker/Podman or any hosted backend/database service, then prove the real browser → API → PostgreSQL request/data path.

Do not spend the session repeating the work already committed. Inspect the live repository at the exact current main SHA first, inspect current CI, and continue from the implementation below.

## Hard operating constraints

1. Use only tools/resources that are genuinely free and will not incur charges.
2. Do not use TinyFish, paid agents, paid runners, paid databases, cloud compute, paid upgrades or metered infrastructure.
3. Do not create/restart/resize/deploy Fly machines, do not restore the paused Supabase project, do not create a new Supabase project, and do not provision OCI, Vercel or other hosted backend/database infrastructure.
4. Do not use Docker, Podman or containers on the user's Windows machine. The intended local runtime is native PowerShell + Java + Maven + PostgreSQL + Keycloak + Node/Vite.
5. Do not expose or request the contents of state.json, accounts.txt, .env files, database passwords, OIDC tokens, service credentials or private keys. Only request redacted logs.
6. Do not use force-push, reset/rewrite shared history, or work on side branches. Preserve the repository's direct-main workflow.
7. Read the current repository and CI output. Separate verified facts, code changes, actual validation, blockers and outstanding work.
8. Never claim Windows runtime success unless the script has actually been executed on the user's Windows machine and the required API, database and OIDC tests have passed.

## Already implemented on main

Inspect these exact paths before making changes:

- scripts/Start-UEP.ps1 — native Windows launcher with Demo, Real and Both modes.
- startuep.ps1 — compatibility wrapper to the new launcher; no old machine-specific path.
- scripts/verify-local-native-launcher.py — static launcher contract checks.
- scripts/verify-local-deployment-boundary.py — verifies hosted backend/database deployment entry points remain retired.
- docs/operations/LOCAL_NATIVE_WINDOWS.md — local setup and operations guide.
- docs/operations/LOCAL_NATIVE_WINDOWS_TEST_PROMPT.md — full browser/API/PostgreSQL acceptance plan.
- docs/operations/FLY_SUPABASE_STAGING.md — retired hosted deployment and honest decommission record.
- docs/operations/ACTUAL_STAGING_DEPLOYMENT.md — historical/retired notice.
- docs/operations/OCI_CLOUDFLARE_STAGING.md — historical/retired notice.
- docs/operations/VERCEL_DEPLOYMENT_READINESS.md — historical/retired notice.
- docs/CLOUDFLARE_DEMO_DEPLOY.md — frontend-only offline demo description.
- README.md — current local-first status and commands.
- apps/web/vite.config.ts — /api proxy points to http://127.0.0.1:8080.
- apps/web/src/platform/api-core.ts — authenticated fetch helper, with same-origin API path in Real mode.
- apps/platform-server/src/main/java/com/universalplatform/DatabaseMigrationRunner.java — explicit one-shot Flyway migration runner.
- infra/keycloak/education-realm.json — local education realm and platform-web client.

The launcher performs an initial prerequisite inventory before installation or startup. It checks repository files, Node/npm, JDK, Maven, PostgreSQL tools/service, Keycloak distribution and local state; then installs/offers missing free prerequisites through WinGet or official downloads, verifies Maven SHA-512 and Keycloak SHA-256, initializes a dedicated local PostgreSQL database/roles, runs the migration runner, starts Keycloak/API/Vite, prints URLs and live statuses, and stops only the processes/services it started. It preserves local database contents.

## Launcher modes and endpoints

From the repository root:

- Both: .\scripts\Start-UEP.ps1 -Mode Both
- Real: .\scripts\Start-UEP.ps1 -Mode Real
- Demo: .\scripts\Start-UEP.ps1 -Mode Demo
- No automatic browser opening: add -NoBrowser
- Skip additional test-persona creation: add -SkipAccountSeeding

Real mode:

- Frontend: http://localhost:5173
- Backend: http://localhost:8080
- Keycloak: http://localhost:8081
- PostgreSQL: 127.0.0.1:5432, database uep_local_native

Both mode additionally starts the offline Demo frontend at http://localhost:5174.

Demo mode is deliberately offline and fixture-backed. It does not start or use the API, Keycloak or PostgreSQL. It is not evidence of a real-data workflow.

## Intended Real-mode data path

Browser at localhost:5173
→ React/Vite
→ same-origin fetch calls to /api/...
→ Vite proxy to 127.0.0.1:8080
→ Spring Boot authentication/tenant/authorization/domain services
→ PostgreSQL uep_local_native as uep_local_runtime.

Authentication is separate:

React/Vite
→ Keycloak 26.7.5 at localhost:8081
→ realm education, browser client platform-web
→ Authorization Code + PKCE S256
→ signed JWT with tenant_id
→ Spring Boot verifies issuer/JWKS and tenant context.

Database roles:
- uep_local_migrator — one-shot migrations/schema owner only.
- uep_local_runtime — restricted Spring API login; no DDL, superuser, RLS bypass, role membership or object ownership.
- uep_local_keycloak — Keycloak login, own keycloak schema only.

Spring runtime must use SPRING_FLYWAY_ENABLED=false after the one-shot DatabaseMigrationRunner has applied and validated migrations. Do not run the API as postgres or the migration role.

Local generated credentials are stored outside Git under %LOCALAPPDATA%\UEP\local-native\. Generated command files/logs/tools are under the ignored .uep-local directory. Do not ask the user to paste credential files.

## Current hosted-resource decommission boundary

Verified state from the previous session:

- Fly app uep-backend-testing has a user-reported Sydney machine d89565db307d58, stopped after 10 failed starts, with no backend volume. It has **not** been permanently deleted from the Fly account.
- Fly Keycloak app uep-keycloak-testing does not exist.
- Supabase UEP project ref rykmwcslbzmkzmnnzvxm was paused successfully and a subsequent check reported INACTIVE. It is paused, not permanently deleted.
- No UEP database migrations were applied to that hosted database.
- Cloudflare Pages projects remain separate frontend-only projects. The last observed testing bundle was stale/unqualified; the demo project is offline fixture-based.
- Fly API/Keycloak manifests, hosted Supabase database-migration workflow, remote staging E2E workflow, Vercel multi-service configuration, Vercel backend profile and Vercel-specific build scripts have been removed from main. Dockerfiles remain only for CI container-build/smoke qualification; the local launcher does not use them.

Do not tell the user these provider resources were deleted. The stopped Fly machine/app and paused Supabase project still need owner-side dashboard deletion if permanent deletion is desired. Do not perform those actions without account access and do not start any resource.

## Immediate next-session work — execute in order

### 1. Sync and establish evidence
- Fetch origin/main and inspect the exact current head SHA.
- Confirm the working tree and local branch state without destroying user changes.
- Read the current CI run for that exact SHA. The prior session had multiple commits queued in GitHub Actions due direct-main commits; do not mistake an older successful run for the final main head.
- Inspect all failed steps/logs, especially repository-policy, local deployment boundary, local native launcher contract, PowerShell parser and production OIDC frontend build.
- Fix only real root causes; do not weaken assertions or skip jobs to obtain green CI.

### 2. Static launcher validation
- Run the current repository checks and Python verifiers.
- Confirm PowerShell 5.1/7 syntax is parsed by the GitHub-hosted PowerShell CI step.
- Verify the actual launcher and the root wrapper.
- Validate that no hosted deployment manifests/workflows have reappeared.
- Inspect all generated command-file arguments and ACL boundaries carefully. The user must never be asked to publish service credentials.

### 3. Windows machine runtime acceptance
The local Windows runtime has **not yet been proven** by the previous conversation. Do not claim it is working until the owner executes the script.

Ask the owner to sync main and run:
    .\scripts\Start-UEP.ps1 -Mode Both

PowerShell may need to run as Administrator if the PostgreSQL Windows service is stopped and Windows denies Start-Service. The first-run database bootstrap asks for the local postgres superuser password; this is entered interactively and must not be pasted into the chat. Missing free prerequisites may be installed through WinGet or official Apache/Keycloak/PostgreSQL distributions.

When the user shares logs:
- Request only redacted log excerpts and status output, never state.json/accounts.txt.
- Diagnose root causes from .uep-local\logs and actual service output.
- Confirm PostgreSQL accepts connections on 5432.
- Confirm Keycloak education discovery is HTTP 200.
- Confirm Spring Boot actuator health is HTTP 200 and status UP.
- Confirm Real Vite app on 5173 and Demo on 5174.
- Confirm the script stops app processes on Ctrl+C and preserves the local database.
- If any service fails, patch and retest the local launcher/CI rather than presenting a partial startup as success.

### 4. Prove browser → API → PostgreSQL for Real mode
Use docs/operations/LOCAL_NATIVE_WINDOWS_TEST_PROMPT.md as the authoritative acceptance checklist.

At minimum:
- Sign in through the real Keycloak OIDC flow.
- Confirm platform-web uses Authorization Code with PKCE S256.
- Confirm GET /api/v1/me resolves the local owner, tenant, membership and roles.
- Use browser Network tooling to prove a real UI data load calls /api/... through Vite and Spring Boot.
- Create or update a uniquely named test record through the UI/API, verify the HTTP method/status, verify the corresponding PostgreSQL row, reload/new browser context and prove the data persists.
- Cover GET, POST, PUT, PATCH and DELETE only on actual existing documented operations.
- Verify backend request datasource user is uep_local_runtime, never postgres/uep_local_migrator.
- Verify Keycloak tables live in keycloak schema and UEP application tables live in public.
- Verify no browser connection or credential exposure to PostgreSQL.

### 5. Complete role and business workflow coverage
Inspect OpenAPI, controllers, DTOs, security rules and existing tests before inventing any test input. Exercise workflows that the current product actually implements:
- Organization/branch creation and branch-scoped role assignment.
- Membership and role permissions.
- Academic period/program/course/batch, enrollment and teacher assignments.
- Teacher assignment/assessment creation and publication.
- Student submission and teacher grading/result publication.
- Attendance.
- Announcements, notifications and messaging where functional.
- Guardian access only to linked learners.
- Tenant and branch negative access tests.
- Support/auditor read-only restrictions.
- Revoked/ended membership and session behavior.

Use the local test personas provisioned by the launcher. The Branch Administrator must remain branch-scoped, never tenant-wide. Verify this from API results and database rows.

### 6. Add durable tests and fix product wiring
- Add real-mode Playwright tests that exercise the local API and persistence.
- Add API integration tests where useful.
- Add database assertions that verify persisted writes and readbacks.
- Ensure frontend errors do not turn failed requests into false success.
- Keep the Demo fixture tests separate and clearly labeled as not Real mode.
- Verify all frontend actions that require server data use the shared authenticated API client; do not add direct database access to the browser.
- Verify every data mutation is persisted through Spring service/repository/transaction paths, not frontend localStorage.
- Add integration proof to CI only where it remains zero-charge and does not require remote infrastructure. Do not add paid resources.

### 7. Documentation and handoff
- Update LOCAL_NATIVE_WINDOWS.md and LOCAL_NATIVE_WINDOWS_TEST_PROMPT.md after actual Windows runtime discoveries.
- Keep the hosted retirement record accurate; never claim provider deletion without checking the provider dashboard.
- Report exact tested commands, output, test counts, CI run URLs and commit SHAs.
- Separate PASS, FAIL, BLOCKED and NOT TESTED.
- Finish with a concrete next milestone, not an open-ended request to "continue".

## Current expected final acceptance state

Do not call UEP locally qualified until all of these are evidenced:
- Demo mode starts offline and stays isolated.
- Real mode starts native PostgreSQL, Keycloak, Spring API and Vite.
- Real browser OIDC sign-in succeeds.
- Browser-to-API proxy request succeeds.
- Backend reads/writes the intended PostgreSQL database with the least-privilege runtime role.
- At least one UI-originated write is verified in PostgreSQL and persists after reload/restart.
- GET/POST/PUT/PATCH/DELETE are exercised against real supported API routes.
- Tenant/branch/role isolation tests pass.
- Frontend lint/tests/build, backend Maven verify, PowerShell parsing, launcher static checks and repository policy all pass on the exact current main SHA.

Start by syncing and checking the live repository/CI. Do not ask the user to repeat anything already present in this handoff or in the repository.


---

## Continuation update — 2 November 2026

The original handoff instructions remain authoritative. Continue by fetching the live `main` head and reviewing the latest CI run before making further changes.

### Engineering changes after the original handoff snapshot

- Removed duplicate `Start-ManagedProcess`, `Write-CommandFile` and `Wait-ForHttp` definitions from the native launcher. The single retained HTTP waiter accepts only HTTP 2xx responses; a 4xx response is not readiness.
- PostgreSQL tool/service discovery now selects only supported major versions 17 and 18 by numeric version, and prefers an already-running supported service over lexicographic directory-name ordering.
- Explicit current-user-only file ACLs are applied to local state, temporary database-role bootstrap SQL, generated command files containing service environment values, and the local account file.
- The frontend analytics CSV request now uses the same API transport as JSON requests, including the refreshed Keycloak bearer token, same-origin Vite proxy path and consistent API error handling.
- Added a frontend request-contract test and backend CSV export authorization/response integration test. Added an isolated native Real Playwright smoke configuration and test for the local owner OIDC login, authenticated `/api/v1/me`, analytics overview and CSV export flow.
- The native Keycloak distribution pin is now 26.7.5 in the same 26.7 release line. The official release asset SHA-256 is pinned and verified before extraction.

### Validation boundary

These changes are committed source and automated-test additions. They are not evidence that Windows services have launched on the owner's machine. Do not report PostgreSQL initialization, Keycloak login, the Spring Boot datasource, browser proxying, persisted application writes, role/tenant isolation or restart persistence as passing until the corresponding local commands, browser requests and PostgreSQL readbacks have actually run. CI statuses must be read from the resulting current `main` SHA; queued or older runs are not passes.

The native Playwright smoke now covers owner OIDC, same-origin authenticated analytics/export calls, a UI-created tenant branch, reload/API readback, and a direct PostgreSQL query as `uep_local_runtime` with negative CREATE/Flyway-history checks. It retains one annotated `NATIVE-E2E-<timestamp>` branch per run because no branch-delete API exists. CI only discovers/parses this test; it does not start the Windows services or execute the local browser/database flow. Run the suite on the actual Windows machine, then continue with the wider browser-to-API-to-PostgreSQL workflows and tenant/branch/role negative tests described in `LOCAL_NATIVE_WINDOWS_TEST_PROMPT.md`.


### Clean-checkout migration runner requirement

The native launcher's one-shot migration command now executes Maven `compile` before `org.codehaus.mojo:exec-maven-plugin:3.5.0:java`. The CI least-privilege migration probe uses `clean compile` before invoking the same runner, so a previously populated `target/classes` directory cannot mask a missing compile step. Preserve this ordering if the launcher or migration command is refactored.

### Continuation update — 2 November 2026, CI and authorization follow-up

The live repository was rechecked after the previous continuation. The main-line changes since the preceding handoff include:

- `apps/web/test/native/real-analytics-export.native.spec.ts` now exercises the real local owner Authorization Code + PKCE S256 login, checks the active tenant and owner permissions, loads analytics, downloads the authenticated attendance CSV, creates a uniquely named branch through the Organization UI, reloads and reads the branch back through the API, and verifies the exact PostgreSQL row using only the protected `uep_local_runtime` credentials. It checks the runtime role's restricted attributes, no public/database CREATE and no Flyway history access. The branch is retained and its ID/code is recorded in a Playwright annotation because no branch-delete API exists.
- `apps/web/test/api-client-contract.test.mjs` now guards the actual Playwright method expressions (`response.request().method()`) and the computed `PGPASSWORD` environment key. Do not weaken the repository secret scanner to accommodate test source.
- The export integration test's correct authorization contract is both `REPORTS_EXPORT` and `ANALYTICS_VIEW`: the service checks export permission, and the attendance report it invokes independently checks analytics view. The test now covers denial without export permission, denial with export permission but without analytics view, and success when both are present.
- `docs/operations/LOCAL_NATIVE_WINDOWS_TEST_PROMPT.md` was corrected so its native-smoke description consistently matches the committed branch/API/PostgreSQL checks and states the remaining gaps accurately.

### CI evidence at this checkpoint

- The latest observed main SHA before this handoff update was `e3bed7fa33b51606f15def7770d348b76f58e794`.
- CI run #1033 for `8e703a8dd3acf82611861c5d80b32de191113837` completed the web and repository-policy jobs successfully, but both PostgreSQL backend matrix jobs failed because the test did not seed `ANALYTICS_VIEW` for the nested attendance read. This was a real test-fixture omission, not a production code failure; commit `d08a1880b623494eda8f7cdb09e98a8921b872da` restored that baseline permission.
- CI run #1034 for `d08a1880b623494eda8f7cdb09e98a8921b872da` had web and repository-policy jobs pass; backend matrix and other jobs were still running at last inspection.
- CI run #1035 for `9d6a4b1fd722da37f659bc7dca018c83a05b5478` had web and repository-policy pass; backend matrix and Keycloak CI qualification were still running at last inspection.
- CI run #1036 was queued for `e3bed7fa33b51606f15def7770d348b76f58e794` after strengthening the dual-permission integration test. These are not final green results. Fetch the live main SHA and latest workflow status before claiming validation.

Windows-native runtime remains unexecuted in this ChatGPT environment. The launcher, local Keycloak, Spring Boot, Vite, browser-to-API path, database writes/readbacks and restart behavior must still be qualified on the user's actual Windows machine. Never infer these passes from CI or source inspection.

### Continuation update — membership PATCH UI integration

- The frontend API layer now exports `updateMembership`, which sends PATCH to `/api/v1/identity/memberships/{membershipId}` through the same bearer-authenticated `request()` transport.
- The People workspace now exposes membership editing for users with `USERS_MANAGE`: membership status, primary branch (when the user also has `BRANCHES_VIEW` and branch data is loaded), and tenant-local external reference. It sends the current optimistic `expectedVersion`, preserves the existing branch when the user cannot select branches, and refreshes the member directory after success.
- `apps/web/test/api-client-contract.test.mjs` guards the PATCH helper and People UI wiring. The web CI job for commit `0d5e53cd335ce2d8c2f343b107c908c78868117f` passed with 41 frontend tests, Playwright native test discovery (one test), and production build; the later contract-test addition is queued in CI and must still be checked on current main.
- `IdentityAuthorizationIntegrationTest` already protects the final-active-owner invariant and now also covers an owner successfully changing a non-owner membership's status and tenant-local metadata through PATCH. CI for this test addition is pending.
- The native acceptance prompt now explicitly includes the People-workspace PATCH flow and the owner-invariant check.

### Remaining UI/API coverage gaps found during the method inventory

The OpenAPI file currently defines 225 operations: 94 GET, 97 POST, 25 PUT, 1 PATCH and 8 DELETE. The frontend API layer uses a shared authenticated transport, but not every documented operation has a UI action yet. In particular, role-assignment list/add/remove is not yet exposed as a dedicated UI workflow (initial role selection during membership provisioning is available); custom-role update/delete and administrator revocation of another member's sessions also need a deliberate UI coverage decision. Continue mapping actual controllers, authorization rules and current UI before implementing these. Do not claim all 225 operations are wired or end-to-end tested.

At this checkpoint, the live main SHA before this handoff update was `f2bdb1c751ccd46c7cb927606b1120ecab49b0d9`. CI runs #1042–#1045 were queued/in progress; fetch the latest head and workflow status before relying on any result. Windows-native startup and browser-to-PostgreSQL runtime qualification remain unperformed in this environment.
