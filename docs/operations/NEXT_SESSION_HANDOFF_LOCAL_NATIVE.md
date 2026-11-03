# UEP next-session handoff — local Real implementation + offline Demo

Repository: https://github.com/shreyashjagtap157/UEP

Last reviewed: 3 November 2026  
Release: `0.15.0.0-SNAPSHOT`  
Local launcher: `scripts/Start-UEP.ps1`  
Root entry point: `startuep.ps1`

## Mission

Continue engineering and qualifying the actual Universal Education Platform (UEP) implementation on the user's Windows workstation.

The immediate local target is a reliable paired presentation environment:

- **Real UEP** — React/Vite frontend connected to the actual Spring Boot backend and the selected local database.
- **Offline Demo** — separate seeded, browser-local sample experience, used for the simulated Student/Teacher walkthrough.
- **Backend API documentation** — separate Swagger UI and OpenAPI JSON served by the local Spring Boot process.

The default launcher command starts Real + Demo together. These are separate applications and data stores. Never present Demo fixture behavior as proof of Real API or database functionality.

## Non-negotiable operating constraints

1. Inspect the live GitHub `main` branch and exact current commit before editing anything. This handoff can become stale; source and CI evidence take priority.
2. Keep the direct-main workflow. Do not create branches, force-push, rewrite history, or discard user changes.
3. Do not use or start Keycloak for the current local-native walkthrough. Do not probe port 8081, configure an external issuer, or create Keycloak users/database roles. Standard OIDC support remains a separate product integration; it is not part of this local launcher path.
4. Do not use Docker, Podman, Kubernetes, containers, paid agents, hosted databases, hosted compute, paid upgrades or metered services.
5. Do not start or deploy cloud services. Do not resume the paused hosted database or start old Fly machines.
6. Never request, print, commit, upload or expose `state.json`, account files, environment files, database passwords, launcher tokens, service credentials, private keys or credential-bearing JDBC URLs. Ask only for suitably redacted logs.
7. Preserve local user data. Never drop/reset SQLite files, PostgreSQL databases or schemas; never run Flyway clean, destructive restore, truncation or broad cleanup. Do not delete old PostgreSQL Keycloak schema/roles: the new launcher leaves legacy objects untouched.
8. Separate verified facts, source changes, tests actually run, CI results, local Windows execution, blockers, proposals and unresolved questions. Source inspection is not runtime proof.
9. Before pausing work, leave the project runnable through the documented launcher and verify the available startup paths. If workstation access is unavailable, state that actual Windows execution remains outstanding.
10. Do not declare 0.15 complete or production-ready from green CI alone. Production release gates remain broader than local presentation acceptance.

## Current local architecture

### Default launch

From the repository root in PowerShell:

```powershell
.\startuep.ps1
```

This is equivalent to paired Presentation mode with SQLite:

```powershell
.\startuep.ps1 -Mode Presentation -Database SQLite
```

Add `-NoBrowser` to keep the browser closed while services start. To run only one environment:

```powershell
.\startuep.ps1 -Mode Real -Database SQLite
.\startuep.ps1 -Mode Demo
```

Optional PostgreSQL qualification (requires local PostgreSQL 17 or 18):

```powershell
.\startuep.ps1 -Mode Real -Database PostgreSQL
```

Supported modes are `Demo`, `Real`, `Both` and `Presentation`. `Both` and `Presentation` launch the same paired Real + Demo topology; Presentation is the default. The root script is a compatibility wrapper for `scripts/Start-UEP.ps1`.

### Endpoints

| Component | Address | Actual role |
|---|---|---|
| Real UEP frontend | http://127.0.0.1:5173 | Actual React application; browser calls same-origin `/api` |
| Offline Demo frontend | http://127.0.0.1:5174 | Seeded simulated personas and browser-local sample data |
| Spring Boot backend | http://127.0.0.1:8080 | Actual local API |
| Swagger UI | http://127.0.0.1:8080/swagger-ui.html | Interactive backend API reference |
| OpenAPI JSON | http://127.0.0.1:8080/v3/api-docs | Generated API contract |
| SQLite database | `.uep-local/uep-local.sqlite` | Default Real-mode local database |

Swagger UI/OpenAPI are enabled and anonymously accessible only under the explicit `local-native` profile. Ordinary secured API operations still require authentication. The backend is bound to loopback.

### Real application request path

```
Browser at 127.0.0.1:5173
  -> same-origin fetch('/api/...')
  -> Vite Node process /api proxy
       -> attaches X-UEP-Local-Auth from process.env.UEP_LOCAL_AUTH_TOKEN
  -> Spring Boot at 127.0.0.1:8080
  -> tenant / identity / authorization / domain services
  -> SQLite (default) or local PostgreSQL (explicit)
```

The random launcher-issued credential is held in ACL-protected local process/command state and supplied to the Real Vite **Node process** as `UEP_LOCAL_AUTH_TOKEN`. The variable is intentionally not prefixed `VITE_`. Vite attaches `X-UEP-Local-Auth` to the upstream proxied API request. The browser bundle, browser storage and browser request headers must not contain the credential. Browser API requests also must not carry a bearer token in this no-Keycloak mode.

The backend local authentication filter accepts the launcher token only for `/api/` routes, loopback requests, a valid unexpired session and one authentication method. It rejects invalid tokens and mixed auth headers. A direct unauthenticated call to `http://127.0.0.1:8080/api/v1/me` must return HTTP 401; a call through Vite succeeds and returns the persisted local owner.

The backend bootstraps only the local tenant, one local owner, active membership and Organization Owner role in the selected database. It does not create synthetic OIDC accounts. The Offline Demo's Student/Teacher personas are synthetic browser-local personas and do not exist as corresponding backend users.

### Database modes

**SQLite (default local workstation path)**

- Spring profiles: `local-sqlite,local-native`.
- Local file: `.uep-local/uep-local.sqlite`.
- No PostgreSQL service or Flyway migrations are used. Hibernate creates/updates the local qualification schema from the JPA mappings.
- Background workers that depend on PostgreSQL-specific behavior are disabled in this local profile.
- The SQLite native browser test checks authenticated identity, branch creation/readback after reload and SQLite file signature.
- This is a constrained local functional qualification profile, not a substitute for the PostgreSQL production/CI data layer. Not every UEP domain workflow is qualified against SQLite.

**PostgreSQL (explicit qualification path)**

- Local PostgreSQL 17/18 only.
- Flyway migrations run via the explicit `DatabaseMigrationRunner` using `uep_local_migrator`.
- Spring Boot runs using the restricted `uep_local_runtime` role with Flyway disabled at runtime.
- The local launcher provisions only migration/runtime roles; it does not create, alter or drop Keycloak roles or schemas. Any legacy objects remain untouched.
- The PostgreSQL native browser suite also checks analytics/CSV, branch persistence, custom-role CRUD, membership and role-assignment lifecycle, and runtime-role privilege boundaries.
- PostgreSQL-only checks must not run against SQLite.

## Launcher behavior now implemented in source

The current launcher was updated to:

- Default both `scripts/Start-UEP.ps1` and root `startuep.ps1` to paired Real + Demo Presentation mode.
- Preserve explicit Demo-only, Real-only and PostgreSQL qualification modes.
- Make Demo-only prerequisite inventory frontend-only; it does not require backend files, JDK, Maven or PostgreSQL.
- Keep backend/JDK/Maven/database checks conditional on Real mode.
- Check ports 5173, 5174 and 8080 before `npm ci`/startup so a second launch does not replace shared dependencies under an active Vite process.
- Remove unused Keycloak installation/account/bootstrap functions from this local launcher and remove local PostgreSQL Keycloak role/schema provisioning. Existing DB objects are left intact.
- Generate a fresh, random, short-lived local API credential and place it only in protected local command/process state.
- Make Vite inject the header server-side and test the direct-backend 401 boundary.
- Detect when a managed service process exits before its readiness URL becomes available; a stale listener on a port is not accepted as sufficient evidence.
- Wait for Real frontend, Vite API proxy, backend health, Swagger UI and OpenAPI document, and verify the OpenAPI title.
- Verify the Vite proxy returns the persisted local owner identity before reporting startup success.
- Show the database/API read as UP only after a successful authenticated identity read via Vite; a non-empty SQLite file alone is not treated as a database health proof.
- Preserve the SQLite DB and local state through clean compile/start/stop. Stop PostgreSQL only if the launcher itself started that Windows service.

`SkipAccountSeeding` remains accepted in the command line for backward compatibility. Local-native now provisions only the persisted owner; this switch does not activate a Keycloak/persona bootstrap.

## Current implementation status and evidence boundary

The repository remains `0.15.0.0-SNAPSHOT`. The source includes substantial implementations across multi-tenancy, identity/RBAC, branches, academic structures, scheduling, announcements/notifications, content/storage, assessments, assignments, grading/gradebook, attendance/live learning, recordings, finance, analytics, integrations and operational/security tooling. The Offline Demo contains seeded role-based browser workflows.

This does not mean every visible module is integrated or qualified end to end in the Real local runtime. Keep distinguishing:

- source implementation;
- offline Demo fixture behavior;
- local SQLite behavior;
- PostgreSQL CI/native qualification;
- actual production/provider readiness.

The most recent code changes reviewed for this local launcher work include commit `d467bd948668c960b384f20a1376a46674c51bbb`; inspect the exact current `main` head before continuing because later commits may have landed.

The previous implementation-status snapshot mentioned earlier CI jobs; those historical jobs do not qualify the code changes made on 3 November. Recheck the current head's GitHub Actions results and inspect failed logs. Empty or missing status records are not a pass. The current-session source review did not execute the Windows launcher, Maven verification, Playwright, or local application on the user's workstation. Do not claim those passed without new run evidence.

## Verification sequence

### 1. Synchronize and inspect

- Fetch and inspect live `origin/main` and latest commit.
- Preserve uncommitted local user changes; do not hard reset or clean the working tree.
- Read current workflow status for the exact head and any failed job logs.
- Review the source of the launcher, wrapper, Vite proxy, auth transport, local-native backend profile, security config, test scripts and current docs.

### 2. Static contract checks

Run from the repository root:

```powershell
python scripts/verify-local-native-launcher.py
python scripts/verify-local-deployment-boundary.py
```

Confirm the verifier covers Demo-only prerequisite scope, paired default, no-Keycloak launcher, server-side proxy credential, no browser token, direct backend 401, process-aware readiness, Swagger/OpenAPI local-only access, database boundaries and preservation of local files.

### 3. Frontend and backend verification

```powershell
npm --prefix apps/web test
npm --prefix apps/web run lint
npm --prefix apps/web run build
mvn -f apps/platform-server/pom.xml verify
```

Run the commands using the repository-supported Node/JDK versions. Report exact exits and any toolchain limitations.

### 4. Actual Windows native launch

Start paired mode:

```powershell
.\startuep.ps1 -Mode Presentation -Database SQLite
```

Before announcing success, verify that the launcher itself reports:

- managed Real Vite process alive and Real page is HTTP 2xx;
- Demo Vite process alive and Demo page is HTTP 2xx;
- Spring Boot actuator health is UP;
- `/api/v1/platform/version` succeeds through the Vite proxy;
- Swagger UI returns HTTP 2xx;
- `/v3/api-docs` returns HTTP 2xx and title `Universal Education Platform API`;
- proxied `/api/v1/me` returns the persisted owner, active membership, tenant ID and Organization Owner role;
- direct unauthenticated backend `/api/v1/me` returns 401;
- browser traffic does not redirect to port 8081 or contact an OIDC issuer.

Use browser labels to distinguish Real and Offline Demo. Do not infer a pass from a TCP listener alone.

### 5. Native browser-to-database test

Install Playwright Chromium once, then with the launcher still running:

```powershell
npm --prefix apps/web exec -- playwright install chromium
npm --prefix apps/web run test:e2e:native
```

Run under the same Windows user profile that initialized protected local state. The native suite chooses the matching SQLite or PostgreSQL test based on the selected provider. It must prove browser request headers contain neither the local credential nor bearer Authorization, while API calls succeed through Vite. The SQLite suite verifies a branch write/readback and SQLite file; PostgreSQL additionally verifies the relevant persisted records and restricted database role.

The tests intentionally annotate/retain resources for which no safe API deletion exists (for example a unique branch or test membership). Do not delete those records with direct SQL.

### 6. Manual end-to-end walkthrough

**Real UEP:** verify current identity and tenant, create a uniquely named branch from Organization, confirm POST response, reload, and read it back through GET. Only present other module actions if they have actually been rehearsed against the selected database mode.

**Offline Demo:** Student Today, My Learning, a prepared assignment, then Teacher submission queue/grade, then Student Results & feedback. State clearly that all such records are browser-local simulated data.

**Swagger:** open the separate API reference and inspect API paths/schemas. Swagger documentation access does not bypass auth on real business API methods.

### 7. Stop and handoff

Use Ctrl+C in the launcher window. Confirm application child processes launched by the script stop, while the SQLite file, PostgreSQL database, and protected local state remain preserved. If PostgreSQL was already running before launch, the launcher must not stop it.

## Known completion gaps / production release gates

Continue using `docs/IMPLEMENTATION_STATUS.md` and `docs/MASTER_IMPLEMENTATION_PLAN.md` as the source for the broader product roadmap. The status snapshot has listed unfinished or incompletely qualified areas including:

- persistent communication threads, delivery/read state, moderation and abuse controls;
- advanced academic moderation/double grading/adjudication and reproducible regrading;
- device-based attendance and offline synchronization;
- configurable workflow engine and history;
- permission-aware global search;
- finance edge cases, reconciliation/accounting exports and real payment provider qualification;
- complete federation and interoperability (LTI, CASE, xAPI, Caliper, SCORM, mature OneRoster/QTI and Open Badges/CLR);
- credential portability and tenant white-label capabilities;
- live-class reconnect/recovery and scale qualification;
- crash-safe recording workers, transcoding, archive/CDN and recovery;
- longitudinal analytics and intervention workflows;
- PWA/offline capabilities and native clients.

Production Web GA still requires independently evidenced security/penetration testing, p50/p95/p99 capacity tests, WCAG 2.2 AA review, PostgreSQL PITR/full restore and object-storage recovery, measured RPO/RTO, failure injection, provider degradation/failover, migration/upgrade/rollback tests, and operational readiness.

## Reporting format for the next session

Keep the report in this order:

1. Exact current main SHA and working-tree status.
2. Current CI run and each required job's terminal state.
3. Verified implementation/source changes.
4. Commands actually executed and their exit codes.
5. Actual local HTTP/browser/API/database evidence.
6. Demo-vs-Real boundaries.
7. Failures, blockers and remaining gaps.
8. Commits made and the next concrete milestone.

Do not label a task complete just because a file or workflow check was added.