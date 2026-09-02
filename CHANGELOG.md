# Changelog

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
