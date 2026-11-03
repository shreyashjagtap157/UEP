# Universal Education, Training and Learning Operations Platform

## Current repository status — 3 November 2026

> **Release gate: NOT QUALIFIED FOR PRODUCTION.** UEP is being qualified locally because its hosted backend and database staging path has been retired. A green source/build CI run is not a connected production deployment.

> **Last observed hosted service status (2 November 2026; not rechecked in this local launcher review):** the Fly API machine previously reported by the owner was stopped (Sydney, machine d89565db307d58, no attached volumes); the Fly Keycloak app did not exist. The Supabase UEP Free project was paused and subsequently reported INACTIVE. No UEP migrations had been applied to that hosted database. The Fly app record and paused Supabase project had not been permanently deleted; see the retired hosting record at [FLY_SUPABASE_STAGING.md](docs/operations/FLY_SUPABASE_STAGING.md).

> **Remote deployment controls retired in Git:** Fly app manifests and the hosted database-migration workflow have been removed from main. The Cloudflare Pages frontend projects remain separate; the last inspected testing bundle was stale and must not be treated as a connected product. No remote API or OIDC service is currently qualified.

> **Supported local path:** from the repository root, run `.\startuep.ps1` (Presentation mode is the default). It starts the actual Real React/Vite frontend + Spring Boot API + local SQLite database and the separate browser-fixture Demo side by side. Use `-Mode Real`, `-Mode Demo`, or `-Database PostgreSQL` to select another path. Keycloak is not installed, started or contacted by the local launcher. A random launcher credential stays in the protected Node/Vite process and is injected into upstream `/api` proxy requests; the browser bundle and browser request headers do not receive it. The current Windows runtime still requires acceptance testing on the target workstation.

> The current 0.15.0.0-SNAPSHOT source contains tenant-scoped RBAC, academic workflows, assessments, assignments, grading, communication, storage, live-learning and operational modules. **Feature completeness is not equivalent to local acceptance:** run the test plan in [LOCAL_NATIVE_WINDOWS_TEST_PROMPT.md](docs/operations/LOCAL_NATIVE_WINDOWS_TEST_PROMPT.md) and record actual browser, API, database and isolation evidence before making product-readiness claims.

## 0.15.0.0 — Reliability and Performance Qualification

Hardening milestone covering repeatable load probes, database indexes, connection-pool bounds, backup/restore drill tooling, tenant-isolation checks, and security/configuration qualification.

A web-first, API-first, multi-tenant education and training operations platform.
The repository follows the implementation roadmap in `docs/MASTER_IMPLEMENTATION_PLAN.md`.

## Current milestone

`0.15.0.0-SNAPSHOT` — Reliability and Performance Qualification.

The current hardening milestone is implemented in source and intentionally remains a snapshot until its full release gates are verified with the production toolchain and services.


## Implemented through Grading and Academic Review

- OIDC-backed multi-tenant identity, memberships, RBAC, branches, organization settings, session revocation, and strong-authentication integration.
- Flexible academic periods and programs without forcing every organization into one fixed hierarchy.
- Courses, subjects, and modules with explicit domain boundaries and optional parent relationships.
- Batches/cohorts with branch and academic references, lifecycle, capacity, and concurrency-safe seat allocation.
- Learner enrollments with exactly-one-target integrity, lifecycle history, duplicate prevention, and self-scoped learner APIs.
- Teacher/evaluator assignments with exactly-one-scope integrity and self-scoped teaching APIs.
- Role-aware web dashboards plus academic, curriculum, enrollment, and teaching-administration workspaces.
- PostgreSQL tenant-aware composite foreign keys across academic relationships and optimistic concurrency on editable records.
- Finite IANA-timezone recurrence, materialized schedule occurrences, stable class-session anchors, conflict detection, audited overrides, rescheduling, cancellation, and substitutes.
- Organization/branch/course/batch/subject/role/member announcements with scheduled publication, acknowledgement, and publication-time audience snapshots.
- Transactional notification outbox with in-app inbox, optional SMTP delivery, per-event preferences, bounded retries, dead-letter visibility, and tenant-scoped operations.
- Universal Today dashboard that remains self-scoped for learners/teachers and scope-aware for administrators.
- Provider-independent versioned learning resources with textual notes and file-backed content.
- Streamed/resumable offset-based uploads with exact-length and optional SHA-256 verification.
- Local, S3-compatible, Google Drive and Shared Drive storage adapters with server-owned provider locators.
- Server-side visibility/download policies, release/expiry windows, and tenant retention configuration.
- Authenticated Learning Content workspace for authoring, uploading and browsing resources.
- Immutable system/teacher/reconciliation grade revisions, result publication, objective grading, and bounded teacher overrides.
- Academic review cases with discussion, answer-revision proposals/decisions, impact analysis, and review-driven regrading.
- Learner results/review and teacher/evaluator grading/reconciliation workspaces in the first-class web application.
- Stable grading/review APIs kept independent of the web client for future native applications.
- Live classrooms with provider-independent media boundaries, LiveKit integration, presence, attendance policies, moderation state, screen sharing, low-bandwidth profiles, and chat.
- Recording platform with asynchronous LiveKit Egress capture, processing states, quality presets, local/S3/Google storage adapters, hot/cache/archive transitions, retention, signed playback authorization, audit trails, and dynamic web playback watermarks.

## Repository layout

- `apps/platform-server` — Java 25 / Spring Boot control plane and domain platform.
- `apps/web` — React + TypeScript web client.
- `infra` — local/deployment infrastructure definitions.
- `docs` — architecture, API, commercial/licensing, ADRs, and master plan.
- `scripts` — repeatable repository verification helpers.

## Architecture rules

1. The server is authoritative for authorization, tenancy, licensing, grading,
   academic state, payments, recording access, storage policy, and audit history.
2. Modules communicate through explicit APIs/events; repositories are not shared
   across domain-module boundaries.
3. Commercial capabilities are controlled through entitlements and limits, never
   hard-coded plan-name conditionals.
4. Provider-specific storage/media/client logic is kept behind adapters.
5. A tenant identifier from trusted authentication context is mandatory for
   tenant-owned operations.

## Required production toolchain

- JDK 21+ (CI currently verifies on JDK 25; Maven compiles to Java 21 bytecode)
- Maven 3.9+
- Node.js 22.22.0+
- PostgreSQL 18
- Keycloak 26.7.x for the reference identity deployment
- Valkey 9.1.x optional
- Docker/Podman for the reference development environment

## Build

```bash
mvn -f apps/platform-server/pom.xml verify
npm --prefix apps/web ci --ignore-scripts
npm --prefix apps/web test
npm --prefix apps/web run build
```

## Native Windows local development

The launcher inventories local requirements before startup. It can install Node.js LTS and Eclipse Temurin JDK 21 through WinGet, downloads a SHA-512-verified Maven 3.10.0 into the ignored `.uep-local/tools` directory, installs locked frontend dependencies with `npm ci`, and resolves backend dependencies with Maven. The local Real launcher does not install, start or contact Keycloak. Its launcher-issued API token is injected by the Node-side Vite proxy; it is not embedded in browser JavaScript or sent in browser request headers. Use a full JDK (including `javac.exe`), not a JRE. When WinGet is unavailable, use the manual installer links in [the Windows setup guide](docs/operations/LOCAL_NATIVE_WINDOWS.md).

No container runtime is required. The launcher checks prerequisites and starts Spring Boot and Vite as native Windows processes. SQLite is the default local qualification database and does not require PostgreSQL or Flyway; select `-Database PostgreSQL` only when explicitly qualifying the PostgreSQL migration/runtime-role path, which runs Flyway separately with the local migrator role before the API starts with the restricted runtime role.

```powershell
# Default: start the actual Real implementation and the separate offline Demo together
.\startuep.ps1

# Explicit paired mode, or run just one environment
.\startuep.ps1 -Mode Presentation -Database SQLite -NoBrowser
.\startuep.ps1 -Mode Real -Database SQLite
.\startuep.ps1 -Mode Demo

# Explicit PostgreSQL migration/runtime-role qualification (requires local PostgreSQL 17/18)
.\startuep.ps1 -Mode Real -Database PostgreSQL
```

Local backend API reference (available only while Real runs with the local-native profile):

- Swagger UI: `http://127.0.0.1:8080/swagger-ui.html`
- OpenAPI JSON: `http://127.0.0.1:8080/v3/api-docs`

See [Native Windows local development](docs/operations/LOCAL_NATIVE_WINDOWS.md) for requirements, URLs, local authentication, Swagger UI, SQLite/PostgreSQL boundaries, startup/stop behavior and local state. See [the UEP presentation runbook](docs/presentation/UEP_PRESENTATION_RUNBOOK.md) for the paired Real + Demo presentation. See [the local integration acceptance prompt](docs/operations/LOCAL_NATIVE_WINDOWS_TEST_PROMPT.md) for browser-to-API-to-database qualification.

For **all Real-mode launches**, whether SQLite or PostgreSQL, the backend provisions only the persisted local API owner, active membership and Organization Owner role. The no-Keycloak local-native launcher does not create OIDC test personas or Keycloak database roles. The Offline Demo has its own separate seeded browser personas; those synthetic personas are not backend accounts.

The launcher uses `PLATFORM_DEV_MODE=true` only for local non-production execution. Never enable this setting with a production Spring profile.

After starting Real mode with `.\startuep.ps1 -Mode Real -Database SQLite -NoBrowser` or Presentation mode, run the database-specific local-auth/API browser smoke from a second PowerShell window (install Chromium once as needed):

```powershell
npm --prefix apps/web exec -- playwright install chromium
npm --prefix apps/web run test:e2e:native
```

The SQLite native spec verifies that direct unauthenticated backend access returns 401, the Real browser loads the persisted owner through the Vite proxy without receiving the local credential, a branch write is read back after reload, and the SQLite file has the expected signature. The PostgreSQL native spec additionally verifies analytics/CSV, custom-role CRUD, membership/role lifecycle and restricted runtime-role grants. Each database mode has a separate evidence boundary. Each run retains its unique test branch because no branch-delete API exists. These focused checks do not replace the wider multi-tenant/branch isolation matrix, all business workflows or restart-persistence acceptance.

## Licensing

First-party code is proprietary. See `LICENSE` and
`docs/commercial/LICENSING_ARCHITECTURE.md`. Customer-facing feature access is
represented by server-side subscriptions, entitlements, limits, and usage—not by
source-code forks or frontend-only switches.


## Client strategy
The web application is the first production client. Native desktop and mobile applications will be built later against the same stable platform APIs; business rules remain server-authoritative.


## 0.12 Analytics and Operations
The current milestone adds tenant-scoped analytics, operational reporting, recording/storage forecasting, and bounded CSV exports. See `docs/architecture/ANALYTICS_AND_OPERATIONS.md`.


## 0.14 Integration Platform
Tenant-scoped API credentials, signed webhooks, OpenID Connect federation configuration, OneRoster 1.2 CSV interoperability, QTI 3.0 assessment-item exchange, external storage binding, and external notification providers are now available behind explicit adapters and RBAC.

## 0.13 Advanced Academic Platform
Learning outcomes, competencies, learning paths, credentials, mentoring, surveys, and feedback are exposed through server-authoritative, tenant-scoped APIs and the web-first client.
