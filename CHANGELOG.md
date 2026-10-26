# Changelog

## 0.15.0.0 — Reliability and Performance Qualification

Hardening milestone covering repeatable load probes, database indexes, connection-pool bounds, backup/restore drill tooling, tenant-isolation checks, and security/configuration qualification. (Tracked by the milestone gate in `docs/QUALIFICATION_0.15.md`.)

## Unreleased — 2026-10-26

### Added
- durable per-recipient messaging receipts with monotonic PENDING → DELIVERED → READ transitions, participant-scoped visibility, and sender inspection of delivery/read state;
- tenant-scoped messaging policy controls for enablement, student-to-student restrictions, retention periods, message send limits and report rate limits;
- atomic PostgreSQL-backed per-membership message/report rate windows and a scheduled batch retention worker that preserves reported evidence until its longer retention period expires;
- messaging block/unblock flows, report submission and moderator resolution/redaction with immutable reported-content evidence snapshots;
- paginated conversation/message/report APIs, caller-specific unread counts, durable recipient notification intents, and an administrator policy panel;
- forward-only V037–V041 migrations for block integrity, reports/moderation, report evidence snapshots, tenant policy/rate windows, and default policy seeding for newly created tenants.
- forward-only V036 database constraints that require messaging conversation creators and participants to belong to the same tenant, and require each message sender to be a participant in its conversation;
- PostgreSQL integration tests for cross-tenant integrity, nonparticipant sends, valid tenant-scoped writes, and messaging API error responses.

### Fixed
- resolved the messaging participant repository's invalid Spring Data derived sort path through the embedded primary key, restoring Spring application startup;
- mapped inactive and cross-tenant membership failures to stable messaging API 403/404 responses without leaking identity-module exception details;
- documented the latest green backend, migration, packaged-server smoke, web, policy, and dependency-scan evidence.


## 0.15.0.0-SNAPSHOT — 2026-10-25

### Added
- local, browser-only demonstration workspaces for student, teacher, institution client, product developer, platform administrator, guardian, exam controller, finance administrator, teaching assistant, evaluator, mentor, support, and auditor personas;
- shared local demo state with same-tab/cross-tab updates and a reset-to-seed workflow;
- cross-persona assignment submission, course-scoped grading, attendance updates, product publishing, and synthetic-data reporting;
- native Node test coverage for assignment/grade propagation, attendance persistence, persona submission scope, rejected mutations, duplicate-title activity isolation, schedule/attendance enrollment and organization isolation, and dataset reset;
- stable record identifiers on new local activity events so assignment/result feeds can verify submission, learner, course, organization, and product relationships;
- deterministic advanced grade-policy calculation supporting drop-lowest, best-of-N, extra-credit, minimum-completion, weight renormalization, validation, and configurable rounding without changing the existing default gradebook behavior.

### Fixed
- renumbered the newer learning/mastery integrity migration to V032 to preserve the existing V029 storage-cleanup migration and enforce unique Flyway versions; added a static uniqueness gate;
- tightened staff and client submission, schedule, attendance, learner roster, catalog and invoice queries to active same-organization learners enrolled in each course; unpublished-course academic records are hidden from learner-facing queries and mutation paths; guardian/mentor learner links now enforce the active identity and organization relationship; exam-controller access requires the exact assigned exam session and matching course;
- blocked exam-linked records from the generic student assignment-submit action, kept exam schedules exclusive to exam controllers, made grading reject cross-organization learners, and prevented new attempts after an exam session is completed or cancelled;
- made local product draft IDs collision-safe when multiple products are created within the same clock millisecond;
- replaced activity-title-only ownership inference with stable submission identity checks and ambiguity-safe exact-title handling for legacy assignment, grade, attendance, progress, organization-action, course-publication and product events; ambiguous duplicate display titles are hidden rather than inferred;
- revalidate local demo identities on fresh sign-in and session restore, and revoke open demo sessions when a shared-state account becomes inactive;
- make the scheduled storage-cleanup poller configurable (enabled by default) and disable it only in its targeted integration test to prevent scheduler races from affecting deterministic worker assertions;
- corrected the pending notification-outbox index to use columns actually present in the schema;
- added forward-only V026/V027 migrations for the live-class batch scope and schedule recurrence column type expected by JPA;
- removed the API-credential authorization package cycle through a narrow security-owned authorization port;
- upgraded affected Maven dependency baselines and made OSV scan the resolved Maven SBOM plus the npm lockfile rather than relying on dependency re-resolution of the POM.

### Demo boundary
- local demonstration accounts use synthetic identities and browser storage only; fresh sign-ins and restored sessions reject suspended users, and open sessions are revoked after a shared-state suspension. Profile selection is not production authentication, and backend authorization remains authoritative for production data and operations.
