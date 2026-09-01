# Universal Education, Training and Learning Operations Platform

A web-first, API-first, multi-tenant education and training operations platform.
The repository follows the implementation roadmap in `docs/MASTER_IMPLEMENTATION_PLAN.md`.

## Current milestone

`0.7.0.0-SNAPSHOT` — Grading and Academic Review.

The learning-content milestone is implemented in source and intentionally remains a snapshot until its full release gates are verified with the production toolchain and services.


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

## Repository layout

- `apps/platform-server` — Java 25 / Spring Boot control plane and domain platform.
- `apps/web` — React + TypeScript web client.
- `infra` — local/deployment infrastructure definitions.
- `docs` — architecture, API, commercial/licensing, ADRs, and master plan.
- `scripts` — repeatable repository verification helpers.

## Architecture rules

1. The server is authoritative for authorization, tenancy, licensing, grading,
   academic state, payments, recording access, and audit history.
2. Modules communicate through explicit APIs/events; repositories are not shared
   across domain-module boundaries.
3. Commercial capabilities are controlled through entitlements and limits, never
   hard-coded plan-name conditionals.
4. Provider-specific storage/media/client logic is kept behind adapters.
5. A tenant identifier from trusted authentication context is mandatory for
   tenant-owned operations.

## Required production toolchain

- JDK 25
- Maven 3.9+
- Node.js 22.12+ (or another Vite 8 supported Node release)
- PostgreSQL 18
- Keycloak 26.7.x for the reference identity deployment
- Valkey 9.1.x optional
- Docker/Podman for the reference development environment

## Build

```bash
mvn -f apps/platform-server/pom.xml verify
npm --prefix apps/web install --ignore-scripts
npm --prefix apps/web run build
```

Development services can be started with `infra/compose.yaml` on a machine with
Docker Compose or compatible Podman tooling.

## Licensing

First-party code is proprietary. See `LICENSE` and
`docs/commercial/LICENSING_ARCHITECTURE.md`. Customer-facing feature access is
represented by server-side subscriptions, entitlements, limits, and usage—not by
source-code forks or frontend-only switches.
