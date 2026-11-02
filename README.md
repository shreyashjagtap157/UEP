# Universal Education, Training and Learning Operations Platform

## Current verified status — 2 November 2026

> **Release gate: NOT YET QUALIFIED FOR PRODUCTION.** The current source-bearing `main` commit is `e6acf6d4d80c057b5f601fcb4c637ee586b15847`. The latest fully completed, all-green main-branch CI run is [UEP CI run #936](https://github.com/shreyashjagtap157/UEP/actions/runs/41424440276) on documentation-only commit `8132e74b135c23a6e0b67c2870f72d54ba351dcb`. All six jobs passed; the executable source in that commit was unchanged from the source commit tested by [run #935](https://github.com/shreyashjagtap157/UEP/actions/runs/41406036125), `a29485b7b73f522a553a94e769a8318d5b774702`.

> The current source commit adds persistence-boundary sanitization for notification outbox, webhook delivery and webhook subscription failure state, retaining only validated exception categories and bounded safe operational labels. It includes direct regression tests for raw provider/credential-like text and null failure inputs. [Run #939](https://github.com/shreyashjagtap157/UEP/actions/runs/41425009098) is running against this exact source commit: web, repository-policy/0.15 qualification, vulnerability scan and Keycloak container jobs have passed; backend Maven verification, PostgreSQL migration/privilege checks, and native API image builds have passed on both PostgreSQL versions; the backend ARM64 image smoke stages remain in progress. Do not treat #939 as a completed full-green run until the run is terminal-success.

> The announcement work remains covered by [run #932](https://github.com/shreyashjagtap157/UEP/actions/runs/41404638692), which passed all six jobs on the announcement fixture restoration commit. Current source includes scheduled publication/expiry retries with bounded backoff, dead-letter replay, tenant/branch-scoped failure operations, publication-time audience resolution, and tests for audience snapshots, replay authorization, retry state and worker isolation.

> These are repository-CI results, not proof of production deployment, live provider connectivity, production-scale capacity, WCAG conformance, or a completed production backup/restore drill. The 0.15 release still requires production-like k6 evidence, live identity/provider qualification, and operational backup/alert/restore runbooks.

> Production and real-staging qualification remain blocked. OCI tenancy/A1 capacity and a stable HTTPS hostname are not verified; R2 remains disabled pending real provider/billing qualification; and the GitHub `staging` and `vercel-database-migrations` Environment branch/reviewer policies remain independently unverified. The runbook also records that the Pages testing project and staging identity matrix need completion. See the [current OCI + Cloudflare staging runbook](docs/operations/OCI_CLOUDFLARE_STAGING.md).

> Pull request #26 remains a separate, materially divergent storage-qualification branch. Its CI is not used as current-main evidence and its branch must not be merged wholesale. See [GitHub Actions](https://github.com/shreyashjagtap157/UEP/actions), [pull requests](https://github.com/shreyashjagtap157/UEP/pulls), the [0.15 qualification guide](docs/QUALIFICATION_0.15.md), and the [master implementation plan](docs/MASTER_IMPLEMENTATION_PLAN.md).

---
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

Development services can be started with `infra/compose.yaml` on a machine with
Docker Compose or compatible Podman tooling.

Local development boots must opt out of the production-secret guard by setting
`PLATFORM_DEV_MODE=true` (see `.env.example`), or by providing real values for
`DATABASE_PASSWORD`, `LIVEKIT_API_KEY`/`LIVEKIT_API_SECRET`,
`PLATFORM_RECORDING_PLAYBACK_SECRET`, and `PLATFORM_INTEGRATION_SECRET_KEY`.

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
