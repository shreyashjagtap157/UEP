# Universal Education, Training and Learning Operations Platform

## Current verified status — 2 November 2026

> **Release gate: NOT YET QUALIFIED FOR PRODUCTION.** Current main is 4dd6754a20f2d5e267dd33c1b2e4bfb2ecb0b0c4. The latest full green run is [UEP CI #941](https://github.com/shreyashjagtap157/UEP/actions/runs/41426195114) on the preceding documentation-only commit. The latest source-bearing full green run is [#939](https://github.com/shreyashjagtap157/UEP/actions/runs/41425009098) on e6acf6d4d80c057b5f601fcb4c637ee586b15847; it passed both PostgreSQL 17.11/18.4 backend matrices, API and Keycloak OCI/ARM64 smoke, web lint/tests/build, repository policy/0.15 qualification and vulnerability scanning.

> The active target staging topology is **Cloudflare Pages frontend → Fly.io Spring Boot API + Keycloak → Supabase PostgreSQL**. The current active runbook is [FLY_SUPABASE_STAGING.md](docs/operations/FLY_SUPABASE_STAGING.md). The existing Pages project and Supabase project are connected, but Pages remains stale/unqualified, Supabase public has no UEP tables, and Fly's live app/machine/volume inventory and Singapore cost have not yet been inspected. No UEP migrations have been applied and no Fly Machines have been started.

> The latest repository changes introduce a dedicated supabase-database-migrations GitHub Environment contract and prepare Fly configuration for Singapore. The migration workflow remains manual-only, main-pinned, and requires explicit confirmation. This does not configure the GitHub Environment's actual branch restrictions/reviewer or create its secrets; those require dashboard verification.

> Supabase is currently a Free-plan staging candidate, not an approved production database: its 500 MB database quota, 1 GB Storage quota, 50 MB maximum upload size, inactivity pause and lack of automatic backups must be accounted for. Fly Machines and volumes are billed resources; do not start or resize them until the account-specific Singapore quote is reviewed and explicitly approved. Cloudflare Pages remains on its Free plan; production/preview auto-deploy remains disabled pending manual qualification.

> The current source includes safe notification/webhook failure persistence at the entity boundaries, durable announcement retry/dead-letter replay and regression coverage. CI is repository evidence only; no production deployment, live identity-provider qualification, production-scale capacity test or production backup/restore drill has been claimed. The retired OCI + R2 runbook is history only; PR #26 remains a separate, materially divergent branch and must not be merged wholesale.

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
