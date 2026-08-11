# Implementation Status

## Current development version

`0.4.0.0-SNAPSHOT` — Scheduling and Communication.

The source implementation for the 0.4 milestone is integrated across the backend and web application. It remains a snapshot because this execution environment cannot run the complete JDK 25 / Maven / PostgreSQL 18 / dependency-backed web release gate.

## Implemented through 0.3 Academic Core

- modular Java/Spring server foundation, PostgreSQL/Flyway, OIDC resource-server security;
- trusted tenant context and tenant-scoped commercial entitlement/limit/usage model;
- signed-offline-license verification primitives and audit foundation;
- external-identity-backed users, memberships, persisted system/custom RBAC, tenant/branch assignments, last-owner protection, and revocable sessions;
- branches and organization defaults;
- academic periods/programs, flexible courses/subjects/modules, batches/cohorts, concurrency-safe capacity allocation, learner enrollments, and teaching assignments;
- self-scoped learner/teacher APIs and role-aware academic web dashboards;
- CI, dependency scanning, proprietary commercial repository controls, and reference deployment infrastructure.

## Implemented for 0.4 Scheduling and Communication

### Scheduling and calendar

- one-time, daily, weekly, and monthly finite schedule series;
- materialized occurrences with original time retained for exception history;
- IANA timezone handling with explicit rejection of invalid and ambiguous DST local times;
- classes, exams, assignments, meetings, events, holidays, and appointments;
- offline, online, hybrid, and not-applicable delivery modes;
- branch, batch, course, subject, module, teacher, and room references where applicable;
- occurrence rescheduling, substitute teachers, room overrides, occurrence cancellation, and series cancellation;
- stable `class_session` records created for class occurrences for later live-class/attendance/recording modules;
- calendar ranges bounded to prevent unbounded reads.

### Conflict safety and authorization

- teacher overlap detection;
- batch/cohort overlap detection;
- room overlap detection within branch;
- exam conflict detection;
- holiday conflict detection;
- tenant-scoped PostgreSQL advisory transaction lock around conflict-sensitive writes;
- explicit `SCHEDULE_CONFLICT_OVERRIDE` authority and mandatory reason;
- persistent conflict-override evidence plus generic audit events;
- tenant-wide administrator calendar scope, multi-branch RBAC scope for branch administrators, and self-scoped teacher/learner calendars.

### Announcements

- organization, branch, course, batch, subject, role, and selected-membership targets;
- normal, important, urgent, and emergency priorities;
- draft, scheduled, published, expired, and cancelled lifecycles;
- future publication and expiration workers;
- publication-time audience resolution so newly eligible users are not missed;
- immutable publication recipient snapshots;
- acknowledgement-required announcements and per-membership acknowledgement timestamps;
- branch-management scope persisted on announcement drafts so guessed identifiers cannot bypass branch authorization;
- attachments intentionally integrate with the 0.5 upload/storage subsystem instead of duplicating storage/security logic in communication.

### Durable notifications and email

- transactional `notification_outbox` boundary used by scheduling and announcements;
- tenant-scoped outbox deduplication;
- composite tenant/outbox database foreign keys preventing cross-client notification linkage;
- per-recipient in-app notifications;
- optional SMTP delivery through Spring Mail;
- explicit `SKIPPED` email state when email delivery is disabled;
- per-event in-app/email preferences with optimistic versions;
- bounded outbox retries and dead-letter state after repeated processing failure;
- bounded email retries and idempotent delivery keys;
- tenant-scoped notification operations counters protected by `NOTIFICATION_OPERATIONS_VIEW`.

### Today dashboard and web application

- universal `/today` backend using organization timezone;
- personal learner/teacher schedule and unread-notification view;
- scope-aware administrative class/exam/scheduled-learner summary;
- responsive Today, Schedule, Announcements, and Notifications workspaces;
- recurring schedule construction and preflight conflicts;
- schedule rescheduling/substitution/cancellation controls;
- targeted/scheduled announcement authoring and acknowledgement;
- notification inbox and per-event channel preferences;
- branch-effective permissions surfaced separately from tenant-wide permissions so the UI can expose branch administration without broadening authority.

### Contract and verification encoded in the repository

- OpenAPI 3.1 synchronized with all 0.4 endpoints and permission keys;
- recurrence unit tests for deterministic weekly materialization and DST-gap rejection;
- PostgreSQL integration tests for schedule conflicts, persisted overrides, and branch administration;
- integration tests proving scheduled announcements resolve recipients at publication time;
- notification tests for outbox idempotency, in-app materialization, recipient read state, tenant-scoped deduplication, and tenant-scoped operations;
- existing tenancy, licensing, identity, organization, academic, enrollment, and teaching tests remain part of Maven verification.

## Verification available in this runtime

The local consolidation pass includes repository version/policy checks, whitespace checks, JSON/XML/YAML parsing, source-level Java parser diagnostics, compilation of the dependency-free recurrence engine, TypeScript diagnostics to the extent possible without installed dependencies, permission-contract consistency, migration dependency checks, Git object integrity, and clean-history checks.

## Environment-limited qualification

This runtime currently has JDK 21 and Node.js but no Maven, Docker/Podman, locally installed project dependencies, or usable dependency-network path for the complete build. Therefore it cannot truthfully claim:

- JDK 25 Maven compilation/test success;
- Flyway execution against PostgreSQL 18;
- Spring application boot against PostgreSQL 18;
- dependency-backed React/Keycloak/TanStack lint and Vite production build;
- live SMTP integration success;
- online OSV dependency resolution;
- complete CI success.

For that reason no `v0.4.0.0` release tag is created here. The repository remains `0.4.0.0-SNAPSHOT` until those gates pass on the required toolchain.

## Next roadmap milestone after qualification

`0.5.0.0` — Learning Content: textual notes/resources, streamed/resumable upload pipeline, versions, download permissions, storage abstraction, local/S3-compatible/Google Drive and Shared Drive providers, and retention.
