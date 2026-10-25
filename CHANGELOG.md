# Changelog

## 0.15.0.0 — Reliability and Performance Qualification

Hardening milestone covering repeatable load probes, database indexes, connection-pool bounds, backup/restore drill tooling, tenant-isolation checks, and security/configuration qualification. (Tracked by the milestone gate in `docs/QUALIFICATION_0.15.md`.)

## 0.15.0.0-SNAPSHOT — 2026-10-25

### Added
- local, browser-only demonstration workspaces for student, teacher, institution client, product developer, platform administrator, guardian, exam controller, finance administrator, teaching assistant, evaluator, mentor, support, and auditor personas;
- shared local demo state with same-tab/cross-tab updates and a reset-to-seed workflow;
- cross-persona assignment submission, course-scoped grading, attendance updates, product publishing, and synthetic-data reporting;
- native Node test coverage for assignment/grade propagation, attendance persistence, persona submission scope, rejected mutations, duplicate-title activity isolation, schedule/attendance enrollment and organization isolation, and dataset reset.
- stable record identifiers on new local activity events so assignment/result feeds can verify submission, learner, course, organization, and product relationships.

### Fixed
- renumbered the newer learning/mastery integrity migration to V032 to preserve the existing V029 storage-cleanup migration and enforce unique Flyway versions; added a static uniqueness gate;
- tightened staff and client submission, schedule, attendance, learner roster, catalog and invoice queries to active same-organization learners enrolled in each course; unpublished-course academic records are hidden from learner-facing queries and mutation paths; guardian/mentor learner links now enforce the active identity and organization relationship; exam-controller access requires the exact assigned exam session and matching course;
- blocked exam-linked records from the generic student assignment-submit action and made grading reject cross-organization learners;
- replaced activity-title-only ownership inference with submission identity checks and conservative resolution of legacy events, preventing duplicate assignment names from crossing learner boundaries;
- revalidate local demo identities on fresh sign-in and session restore, and revoke open demo sessions when a shared-state account becomes inactive;
- make the scheduled storage-cleanup poller configurable (enabled by default) and disable it only in its targeted integration test to prevent scheduler races from affecting deterministic worker assertions;
- corrected the pending notification-outbox index to use columns actually present in the schema;
- added forward-only V026/V027 migrations for the live-class batch scope and schedule recurrence column type expected by JPA;
- removed the API-credential authorization package cycle through a narrow security-owned authorization port;
- upgraded affected Maven dependency baselines and made OSV scan the resolved Maven SBOM plus the npm lockfile rather than relying on dependency re-resolution of the POM.

### Demo boundary
- local demonstration accounts use synthetic identities and browser storage only; fresh sign-ins and restored sessions reject suspended users, and open sessions are revoked after a shared-state suspension. Profile selection is not production authentication, and backend authorization remains authoritative for production data and operations.

## 0.15.0.0-SNAPSHOT - 2026-09-02

### Added
- tenant-scoped API credentials with hashed secrets, expiry, revocation, and permission scopes;
- durable signed webhooks with idempotent event IDs, retry delivery, and delivery auditability;
- OpenID Connect / enterprise federation configuration foundation with authorization URL generation;
- OneRoster 1.2 CSV export/validation foundation for organizations, users, courses, classes, and enrollments;
- QTI 3.0 assessment-item import/export foundation with hardened XML parsing;
- tenant-configurable external storage bindings using the existing storage adapter registry;
- external HTTP JSON, Slack webhook, and Teams webhook notification provider configuration/testing;
- integration RBAC, tenant-safe migration V022, web-first integration administration, and OpenAPI contracts.

## 0.13.0.0-SNAPSHOT - 2026-09-02

### Added
- learning outcomes, competencies, mappings, learning paths, mastery evidence, and progress tracking;
- credential templates, issuance, revocation, canonical verification/QR payloads, and public verification;
- mentoring relationships, first-class survey questions/responses, anonymous response support, and auditable feedback;
- advanced-academic RBAC permissions, tenant-safe migration V021, web-first administration, and OpenAPI contracts.

## 0.12.0.0-SNAPSHOT - 2026-09-02

### Added
- tenant-scoped analytics for attendance, assessments, questions, recordings, finance, usage, and operations;
- bounded 366-day analytics windows with server-side permission enforcement;
- recording/storage growth forecasting based on recent authoritative recording data;
- administrative CSV exports for attendance, assessments, recordings, and finance;
- optimized analytics indexes in migration V020;
- analytics/operations RBAC permissions and synchronized web/API contracts.

## 0.11.0.0-SNAPSHOT - 2026-09-02

### Added
- institution fee definitions with currency and versioning;
- invoices, installments, payments, and receipts with tenant-safe foreign keys;
- provider-neutral payment adapter with reference manual capture flow;
- commercial subscription plans, trials, grace periods, suspension, and optimistic license revisions;
- quota usage service backed by entitlement limits and daily usage periods;
- tenant white-label settings and commercial administration APIs;
- finance and commercialization RBAC permissions and migration V019.

## 0.10.0.0 — Recording Platform

- Added asynchronous recording orchestration backed by LiveKit Egress with explicit requested, starting, recording, finalizing, ready, archiving, archived, failed, and deleted states.
- Added Economy, Balanced, High Quality, and Source Archive recording presets with a provider-neutral media boundary.
- Added storage lifecycle management across hot, cache, and archive tiers using the existing local, S3-compatible, Google Drive, and Shared Drive adapters.
- Added recording metadata/assets, SHA-256 integrity values, thumbnails, retention cleanup, storage-policy versioning, audit events, and ready notifications.
- Added short-lived membership-bound playback authorization and a visible moving identity watermark in the web player.
- Added recording management/read APIs, tenant storage-policy APIs, web recording workspace, and deployment support for LiveKit Egress plus Valkey.

## 0.9.0.0 — Live Learning

- Added provider-neutral live-class domain with LiveKit participant token integration.
- Added web-first live classroom controls, adaptive streaming, low-bandwidth profiles, screen sharing and data-channel chat foundation.
- Added presence heartbeats, participant moderation state, attendance policies and attendance finalization.
- Added tenant-scoped live-class permissions and PostgreSQL live/presence/attendance schema.


## 0.8.0.0-SNAPSHOT — Assignments and Gradebook

- Added tenant-scoped assignments with lifecycle, due dates, weighted grading and batch assignment.
- Added draft, submitted, resubmitted and withdrawn assignment submission states with explicit late-submission tracking.
- Added rubric storage and rubric score payloads on graded submissions.
- Added optimistic concurrency and immutable submitted work semantics.
- Added weighted Gradebook projections and learner progress APIs.
- Added web-first assignment authoring, submission, grading and gradebook workflows.
- Added assignment/gradebook permission contracts, migration hardening and architecture documentation.

## 0.7.0.0-SNAPSHOT — Grading and Academic Review

- Added immutable grade revisions with system, teacher, reconciliation, and regrade sources.
- Added objective grading for supported question types with explicit negative-mark handling and unanswered-answer neutrality.
- Added teacher grading overrides with preserved system/final score history, explanations, and rubric metadata.
- Added result publication and revision history without destructive grade mutation.
- Added academic review cases for grade challenges and answer revisions.
- Added discussion/comments, answer-revision decisions, impact analysis, and review-driven regrading.
- Added tenant-safe grading/review APIs, permissions, database constraints, and audit events.
- Added learner results/review and teacher/evaluator grading/reconciliation workflows to the web client.
- Kept assessment rules server-authoritative so future native clients can reuse the same APIs.


- Added tenant-scoped question bank with immutable question versions.
- Added initial 11-question-type model and versioned JSON payloads.
- Added assessment construction, immutable assessment versions, availability windows and batch assignment.
- Added server-authoritative attempt timing, attempt limits, idempotent answer autosave and submission.
- Added tenant-aware assessment integrity constraints and web assessment workspace.
- Preserved web-first architecture so future native clients consume the same APIs.


The project uses `stable.major.minor.patch` product versioning. Snapshot entries describe committed implementation milestones that have not yet passed the full production release qualification gate.

## 0.5.0.0-SNAPSHOT — Learning Content

- Added versioned textual and file-backed learning resources.
- Added resumable streamed uploads with strict offsets, exact-length finalization, SHA-256 verification, expiry cleanup, and optional ClamAV scanning.
- Added provider-independent storage with local, S3-compatible, Google Drive, and Shared Drive adapters.
- Added server-side resource visibility, release/expiry windows, download policies, historical versions, and tenant retention policy/purge workers.
- Added content RBAC and authenticated Learning Content web workspace.

## 0.4.0.0-SNAPSHOT — Scheduling and Communication

- Added recurrence-aware scheduling, class sessions, conflict controls, announcements, durable notifications/email, preferences, and Today dashboard.

## 0.3.0.0-SNAPSHOT — Academic Core

- Added periods, programs, curriculum, cohorts, enrollment, teaching assignments, and role-aware academic dashboards.

## 0.2.0.0-SNAPSHOT — Identity and Organization

- Added tenant memberships, persisted/custom RBAC, branches, organization settings, strong-auth integration, and revocable sessions.

## 0.1.0.0-SNAPSHOT — Engineering Foundation

- Established the modular monolith, tenancy, audit, commercial entitlement foundation, web shell, CI/security policy, and reference infrastructure.

## Unreleased — Federation claim synchronization and deprovisioning

- Added the public `FederationIdentityDirectory` identity boundary for trusted enterprise federation synchronization.
- Added durable `RoleAssignmentSource` provenance so federation-managed role assignments can be reconciled without deleting manually assigned roles.
- Added tenant-scoped synchronization of federated subjects, profile attributes, branch/external references, group-to-system-role mappings, membership activation, and membership deprovisioning.
- Added removal of previously federated role assignments before applying the current mapped group set, preventing stale external-group permissions.
- Added `POST /api/v1/integrations/identity-providers/{key}/synchronize` and its OpenAPI contract.
- Added migration `V024__federated_role_source.sql` for role-assignment provenance and indexing.
- Preserved the architectural boundary `CLIENTS -> CONTRACTS/APIs -> DOMAIN PLATFORM -> DATA/STORAGE/MEDIA -> INFRASTRUCTURE ADAPTERS`; integration code uses the public identity boundary rather than package-private persistence.
- Qualification: source structure, permission parity, notification parity, 0.15 qualification, OpenAPI parsing, and whitespace checks pass. Full Maven/JDK 25/PostgreSQL runtime qualification remains environment-dependent.
