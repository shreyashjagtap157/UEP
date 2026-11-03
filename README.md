# Universal Education, Training and Learning Operations Platform

## Current verified status — 2 November 2026

> **Release gate: NOT QUALIFIED FOR PRODUCTION.** UEP is being qualified locally because its hosted backend and database staging path has been retired. A green source/build CI run is not a connected production deployment.

> **Hosted service status:** the Fly API machine previously reported by the owner is stopped (Sydney, machine d89565db307d58, no attached volumes); the Fly Keycloak app does not exist. The Supabase UEP Free project was successfully paused and subsequently reported INACTIVE. No UEP migrations were applied to that hosted database. The Fly app record and paused Supabase project have not been permanently deleted; see the retired hosting record at [FLY_SUPABASE_STAGING.md](docs/operations/FLY_SUPABASE_STAGING.md).

> **Remote deployment controls retired in Git:** Fly app manifests and the hosted database-migration workflow have been removed from main. The Cloudflare Pages frontend projects remain separate; the last inspected testing bundle was stale and must not be treated as a connected product. No remote API or OIDC service is currently qualified.

> **Supported local path:** use [scripts/Start-UEP.ps1](scripts/Start-UEP.ps1) with Demo, Real or Both mode. It runs the real React/Vite frontend, Spring Boot API, Keycloak 26.7.5 and PostgreSQL natively on Windows without Docker/Podman or hosted resources. Real mode connects the browser to the API through Vite's /api proxy and connects the API to local PostgreSQL using a least-privilege runtime role. Demo mode is intentionally offline and uses local UI fixtures only.

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

No container runtime is required. The launcher checks prerequisites, starts PostgreSQL, Keycloak, Spring Boot and Vite as native Windows services/processes, runs Flyway separately with the local migrator role, then starts the API with the restricted runtime role.

```powershell
# Run the real integrated application and the offline presentation demo side by side
.\startuep.ps1 -Mode Presentation -Database SQLite

# Real only or offline demo only
.\startuep.ps1 -Mode Real -Database SQLite
.\startuep.ps1 -Mode Demo
```

See [Native Windows local development](docs/operations/LOCAL_NATIVE_WINDOWS.md) for requirements, URLs, SQLite/PostgreSQL boundaries, startup/stop behavior and local credentials. See [the UEP presentation runbook](docs/presentation/UEP_PRESENTATION_RUNBOOK.md) for the paired Real + Demo presentation. See [the local integration acceptance prompt](docs/operations/LOCAL_NATIVE_WINDOWS_TEST_PROMPT.md) for browser-to-API-to-database qualification.

The launcher uses `PLATFORM_DEV_MODE=true` only for local non-production execution. Never enable this setting with a production Spring profile.

After starting Real mode with `.\startuep.ps1 -Mode Real -Database SQLite -NoBrowser` or Presentation mode, run the database-specific native OIDC/API browser smoke from a second PowerShell window (install Chromium once as needed):

```powershell
npm --prefix apps/web exec -- playwright install chromium
npm --prefix apps/web run test:e2e:native
```

The SQLite native spec verifies local owner OIDC sign-in, authenticated identity, a branch write through the UI and API readback after reload, plus the SQLite file signature. The PostgreSQL native spec additionally verifies analytics/CSV, custom-role CRUD, membership/role lifecycle and restricted runtime-role grants. Each database mode has a separate evidence boundary. Each run retains its unique test branch because no branch-delete API exists. These focused checks do not replace the wider multi-tenant/branch isolation matrix, all business workflows or restart-persistence acceptance.

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
