# Universal Education Platform (UEP)
## Consolidated Product, Architecture, Engineering, API, Security, and Qualification Specification

**Spec snapshot:** 28 October 2026  
**Repository:** `https://github.com/shreyashjagtap157/UEP`  
**Branch:** `main`  
**Observed repository head during preparation:** `651b09ae49371e001690e6a734883ff6662741dd`  
**Product version:** `0.15.0.0-SNAPSHOT`  
**Current release posture:** substantial implementation exists and current-main CI passed, but UEP is **not yet qualified for Production Web GA / 1.0**.

This document consolidates the product requirements and implementation contract found in the live repository. It is an implementation companion, not a replacement for the canonical source files linked below. If this document and a canonical source disagree, use the precedence rules in §1 and resolve the discrepancy with a recorded decision rather than silently guessing.

---

# 1. Specification authority and evidence rules

UEP has several complementary source-of-truth layers. Use them for different purposes:

1. **Product scope and long-range requirements:** [`docs/MASTER_IMPLEMENTATION_PLAN.md`](https://github.com/shreyashjagtap157/UEP/blob/main/docs/MASTER_IMPLEMENTATION_PLAN.md). This is the broad product plan and contains 102 numbered sections, from foundational principles through product completion and native clients.
2. **Accepted architecture choices:** [`docs/adr/`](https://github.com/shreyashjagtap157/UEP/tree/main/docs/adr). ADRs 0001–0013 are accepted decisions about modularity, licensing, identity/RBAC, academic modeling, scheduling/outbox, assessments, grading/review, assignments, media, recording, analytics, advanced academics, and integrations.
3. **Domain invariants and boundaries:** [`docs/architecture/`](https://github.com/shreyashjagtap157/UEP/tree/main/docs/architecture). These detail module ownership, lifecycle/state models, security scope, and future dependencies.
4. **Public API contract:** [`docs/api/openapi.yaml`](https://github.com/shreyashjagtap157/UEP/blob/main/docs/api/openapi.yaml). OpenAPI 3.1, version `0.15.0.0-SNAPSHOT`, 174 documented paths at the reviewed head. Treat schemas, status codes, request/response contracts, security declarations, and path semantics there as the contract—not whichever UI implementation happens to be easiest.
5. **Current implementation and limitations:** [`docs/IMPLEMENTATION_STATUS.md`](https://github.com/shreyashjagtap157/UEP/blob/main/docs/IMPLEMENTATION_STATUS.md), source code, Flyway migrations, tests, and CI. Status documentation can lag a newly pushed commit; verify live `main` and its Actions run before making claims about current status.
6. **Qualification requirements:** [`docs/QUALIFICATION_0.15.md`](https://github.com/shreyashjagtap157/UEP/blob/main/docs/QUALIFICATION_0.15.md), `scripts/verify-repository.sh`, `scripts/qualify-0.15.py`, CI, load-smoke helpers, and backup/restore tooling.
7. **How to contribute and release:** [`CONTRIBUTING.md`](https://github.com/shreyashjagtap157/UEP/blob/main/CONTRIBUTING.md), [`docs/VERSIONING.md`](https://github.com/shreyashjagtap157/UEP/blob/main/docs/VERSIONING.md), `VERSION`, and the release scripts.

### Evidence vocabulary

Every implementation report must distinguish:

- **Specified:** required by the master plan, an ADR, architecture note, OpenAPI contract, or acceptance gate.
- **Implemented:** code and schema exist in the current checkout.
- **Covered:** automated tests exercise the behavior, including denied/negative paths where security matters.
- **CI-verified:** the exact commit has passed the applicable CI jobs.
- **Environment-qualified:** the deployed provider, load profile, recovery environment, accessibility audit, or other real operational environment has been tested.
- **Production-ready:** every required release gate has evidence. A successful CI run alone is not sufficient.

Do not promote a requirement from “specified” to “implemented” merely because a page, DTO, adapter stub, migration, or static check exists.

---

# 2. Product definition

UEP is a production-oriented, commercially licensable, multi-tenant education, training, academic, communications, and institutional-operations platform. It must support small coaching institutes, schools, universities, corporate learning groups, and large/enterprise institutions without customer-specific source-code forks.

UEP is **web-first and API-first**. The web app is the first production client. Windows, macOS, Linux, Android, iOS/iPadOS, PWA, kiosk, dedicated enterprise, private-cloud, public-SaaS, and on-premise clients are part of the overall product scope, with production native applications sequenced after the web release and shared contracts stabilize.

The platform’s business logic must remain authoritative on the server. No client is authoritative for tenant identity, access control, exam timing, attempt lifecycle, grading, attendance outcomes, entitlements, payments, recording access, resource download permissions, or audit history.

## 2.1 Product capability map

The finished product combines:

- Organization, branch, member, identity, roles, permissions, sessions, and security administration.
- Flexible academic periods, programs, courses, subjects, curriculum modules, batches/cohorts, enrollments, and teaching assignments.
- Scheduling, recurring timetables, class-session anchors, conflict detection, rescheduling, holidays, announcements, notifications, and Today dashboards.
- Learning content, immutable versions, streamed/resumable uploads, malware-scan boundary, resource visibility, release/expiry windows, storage adapters, retention, and download authorization.
- Question banks, immutable question versions, assessment construction/publication, batch assignment, attempts, timed autosave, submission, grading, result publication, review, challenge, impact analysis, and regrading.
- Assignments, projects, immutable submission attempts, rubrics, deadlines, late work, resubmission, and weighted gradebooks.
- Live classrooms, provider-issued media tokens, audio/video/screen share/chat/moderation, presence, policy-based attendance, and reconnect/low-bandwidth handling.
- Asynchronous recording, capture workers, integrity metadata, quality presets, thumbnail processing, short-lived authorized playback, watermarking, storage tiers, archive, and retention.
- Institution finance (fees, invoices, installments, settlements, receipts), subscriptions, entitlements, quotas, usage metering, trials/grace/suspension, and white-label settings.
- Analytics, reporting, bounded CSV exports, capacity forecasts, operational metrics, advanced academic outcomes/competencies/learning paths, credentials, mentoring, surveys, and feedback.
- Tenant-scoped API credentials, signed durable webhooks, OIDC federation configuration, OneRoster/QTI adapters, external storage and notification bindings.
- Reliability qualification, security scans, tenant isolation, backup/restore, observability, and production operations.
- Optional AI assistance only as a later non-authoritative feature; core operations cannot depend on AI, and AI must not silently alter high-impact academic outcomes.

## 2.2 Explicitly excluded from UEP

UEP does **not** include swarm-agent orchestration, specialist LLM agents, an agent playground, swarm providers, or related developer-agent APIs. Do not introduce such functionality or dependencies into this repository. The local browser-demo personas are synthetic presentation data and are not real backend accounts or a production authentication boundary.

---

# 3. Non-negotiable architecture principles

## 3.1 Domain-driven modular monolith first

The initial application is a Java/Spring modular monolith with Spring Modulith boundaries. Do not split it into microservices merely for appearance or fashion. Service extraction is justified only by evidenced load, isolation, compliance, scaling, or operations constraints.

Each domain module must:

- Own its domain model, tables, repositories, transactions, and invariant enforcement.
- Expose public application/domain interfaces or read-only directories for cross-module use.
- Never reach into another domain module’s repository.
- Publish or consume domain events through explicit contracts, avoiding dependency cycles.
- Have module-specific tests and documented invariants.
- Keep provider SDK details in infrastructure adapters rather than academic/business rules.

## 3.2 Control plane and media plane are separate

**Control plane:** Java/Spring, PostgreSQL, OIDC authentication, tenant/academic models, authorization, exams, grades, billing/licensing, recording metadata, audit, and orchestration.  
**Media plane:** LiveKit/WebRTC SFU, TURN, recording/Egress, transcoding, stream delivery, and other media processing.

Heavy media processing must not run in a synchronous Spring request. Class/session records refer to stable UEP identifiers, never making LiveKit room IDs the domain source of truth.

## 3.3 Provider independence

Provider boundaries include `StorageAdapter`/storage providers, `RecordingMediaProvider`, media/meeting providers, payment providers, OIDC/federation, notification transports, and import/export standards. Switching provider must not require redesigning the academic schema or embedding provider IDs/credentials into domain objects.

## 3.4 API and client independence

All business rules live on the server and are exposed through versioned API contracts. React must not reimplement Java authorization or grading rules. Generated/shared contracts are acceptable; duplicate business logic is not. Future native clients must consume the same API, media-session, file-transfer, notification, licensing, and offline-sync contracts.

## 3.5 Affordable deployment, scalable architecture

A small deployment must be operable on a modest VM/Compose environment without Kubernetes, Valkey, a dedicated analytics warehouse, or dedicated media infrastructure where not required. Larger tenants may later use dedicated DBs/storage/deployments, horizontal media workers, regional deployment, and enterprise isolation. Complexity must follow evidence.

---

# 4. Technology and repository baseline

## 4.1 Canonical stack

- **Backend:** Java 25; Spring Boot; Spring Framework; Spring Security; Spring Modulith; Hibernate/JPA where appropriate; JDBC/native SQL for optimized/reporting queries; Maven.
- **Persistence:** PostgreSQL 18; Flyway migrations; tenant-aware composite constraints; UUIDv7-style IDs where supported/appropriate.
- **Identity:** OIDC, with Keycloak as the reference deployment. The platform does not own passwords, OTP secrets, or passkey private key material.
- **Web:** React, TypeScript, Vite, TanStack Query, `keycloak-js`, LiveKit browser SDK, design tokens and accessible primitives.
- **Short-lived coordination/cache:** Valkey is optional and must not be required for compact deployments unless a specific feature’s availability contract says otherwise.
- **Media:** LiveKit initial implementation; Egress is separate from the application control plane. Keep media adapter boundaries.
- **Storage:** Local filesystem, S3-compatible object storage, Google Drive and Shared Drive adapters are represented. Future adapters may include NAS/NFS/SMB, Google Cloud Storage, Azure Blob, and customer-managed stores.
- **Infrastructure:** Compose/Podman/Docker development environment, reverse proxy, server, PostgreSQL, Keycloak, optional Valkey, LiveKit, and Egress.
- **API:** OpenAPI 3.1, REST `/api/v1`, plus future WebSocket/SSE/media/file-transfer protocols where specified.

Use the versions pinned in the current repository files. Do not casually copy a version mentioned in an older architecture note when `pom.xml`, `package.json`, Compose, or the current lockfile has since changed.

## 4.2 Primary repository layout

- `apps/platform-server` — backend, domain modules, configuration, Flyway migrations, unit/integration/architecture tests.
- `apps/web` — React/TypeScript client, real API workspaces and synthetic local-demo workspaces.
- `infra` — Compose, Keycloak realm, LiveKit/Egress configuration and local storage mount.
- `docs/MASTER_IMPLEMENTATION_PLAN.md` — complete product roadmap.
- `docs/architecture` — domain requirements/invariants.
- `docs/adr` — accepted architectural decisions.
- `docs/api/openapi.yaml` — public API contract.
- `docs/commercial` — subscription/entitlement and offline-license contract.
- `docs/operations` — operational procedures.
- `scripts` — source structure, contract parity, qualification, load-smoke, and restore-drill scripts.
- `.github/workflows/ci.yml` — required checks.

## 4.3 Local development prerequisites

Use JDK 25, Maven 3.9+, Node 22.22+ (per current web package engine/CI), PostgreSQL 18-compatible environment, and Docker Compose or compatible Podman tooling. Keycloak 26.7.x is pinned in the reference Compose. LiveKit and Egress are also defined there.

The project license is proprietary. Do not assume an open-source redistribution right. Preserve third-party licensing/SBOM obligations.

---

# 5. Tenancy, identity, RBAC, and session security

This is a cross-cutting invariant, not one feature among many.

## 5.1 Tenant boundary

- Tenant is the client organization boundary.
- Tenant identity is derived solely from trusted, validated authentication claims (`tenant_id` in the reference OIDC profile).
- Client-supplied headers/body fields must never select or override the authoritative tenant.
- Tenant execution context is set at the request security boundary and cleared at completion; background workers establish tenant context explicitly.
- Tenant-owned repository queries must be tenant-parameterized. Database constraints provide defense in depth.
- Cross-tenant reads or relationships are release-blocking failures.

## 5.2 User and membership model

A global `user_account` is distinct from a `tenant_membership`. A person can have membership in a tenant and multiple role assignments simultaneously. Do not model users as mutually exclusive “teacher” or “student” types.

Membership state and role state must be lifecycle-aware and tenant scoped. Membership provisioning, role assignment, branch assignment, and federation synchronization all pass through the same security/invariant logic.

## 5.3 Identity provider

- Use OIDC Authorization Code with PKCE in the web client.
- Keycloak reference realm: `education`; browser client: `platform-web`.
- The trusted tenant claim must be in the signed token; no local-password system should be added inside UEP.
- TOTP and WebAuthn/passkey enrollment happen at the identity provider through application-initiated actions. UEP records only authentication assurance evidence derived from trusted claims.
- Platform super-administrator-only bootstrap creates the initial Organization Owner, and must refuse a second bootstrap after tenant memberships exist.
- Routine bootstrap/operator access must be removed from ordinary workflows in production.

## 5.4 Roles and permissions

Built-in persisted tenant-local roles include: Platform Super Administrator (platform-level), Organization Owner, Organization Administrator, Branch Administrator, Academic Administrator, Exam Controller, Finance Administrator, Teacher, Evaluator, Teaching Assistant, Mentor, Student, Guardian, Support Operator, and Auditor. Custom roles combine persisted permission grants; do not require source forks to create a role.

Authorization must compose all relevant dimensions:

1. authenticated identity;
2. active membership in the trusted tenant;
3. requested permission;
4. role/branch/resource/context scope;
5. business lifecycle state;
6. subscription/feature entitlement and usage limit where relevant.

A permission alone does not automatically grant access to every resource. For a scoped operation, the request must also target a relationship/resource within the caller’s authorized scope. UI hiding is never server authorization.

**Privileged-role invariant:** only an active Organization Owner or platform super-administrator may grant Organization Owner or Organization Administrator. These roles are tenant-wide. The final active owner assignment cannot be removed; the final owner membership cannot be deactivated, including through federated deprovisioning.

## 5.5 Sessions

- Persist observed OIDC session identity (or token identifier fallback), membership/user context, last-seen, expiry, and platform revocation state.
- Session lists are tenant scoped and paginated.
- Users can revoke their own observed sessions; properly authorized administrators can revoke another member’s session.
- Revocation immediately blocks subsequent requests tied to that observed session identity, independently of the upstream session’s natural expiry.
- Record/audit administrative revocations. During incident response also revoke/logout at the identity provider.

## 5.6 Least-privilege API credentials

- Credential must belong to a real active membership.
- A membership may issue a credential for itself; issuing for another membership requires the specifically privileged owner/platform-super-admin path.
- A credential must specify at least one allowed permission scope.
- Effective permission = intersection of current membership permissions and explicit credential allowlist. Empty scope is not “unrestricted.”
- Secret is shown once; only a SHA-256 hash is persisted. Expiry/revocation is enforced.
- Each request still obeys active membership/tenant permissions. `last_used` updates cannot break the entity’s optimistic locking used by revocation.

---

# 6. Academic core and participation

## 6.1 Flexible curriculum model

The optional hierarchy is `Academic Period → Program → Course → Subject → Module`; a module can have at most one direct parent among program/course/subject. Not every level is mandatory. Support coaching (`Course → Batch`), school (`Grade → Section → Subject`), university (`Program → Period/Semester → Course → Section`), and corporate-training (`Program → Module → Activity`) shapes without separate schema forks.

Domain ownership:

- `academics`: academic periods/programs.
- `curriculum`: courses, subjects, curriculum modules.
- `enrollment`: batches/cohorts, enrollment records, teaching assignments.

Modules call explicit directory interfaces; direct cross-module repository access is disallowed.

## 6.2 Batches and capacity

A batch is an operational cohort and can optionally reference period, program, course, and branch. Validate references are in the same tenant and currently usable. Capacity allocation must lock the batch row before counting active enrollments; optimistic versioning alone cannot prevent two concurrent requests from taking the last seat.

## 6.3 Enrollment invariants

An enrollment belongs to exactly one tenant membership and exactly one target among program, course, or batch. Database and service invariants must agree. Active duplicate enrollments must be blocked by unique constraints/indexes. States include `ENROLLED`, `COMPLETED`, `WITHDRAWN`, and `CANCELLED`. `/api/v1/enrollments/me` is self-scoped regardless of client-supplied membership IDs.

## 6.4 Teaching assignment invariants

A teaching assignment belongs to one active tenant membership and exactly one academic scope among program/course/subject/module/batch. Roles include lead teacher, teacher, teaching assistant, mentor, and evaluator. Active duplicate same-person/scope/role assignments are prohibited. `/api/v1/teacher-assignments/me` is self-scoped; tenant-wide lists require explicit authority.

## 6.5 History and concurrency

Editable entities have optimistic versions. Stale `expectedVersion` must return a conflict rather than silently overwriting a newer change. Preserve historical academic records via state transitions, revision records, and auditable history rather than destructive deletion when downstream records depend on them.

---

# 7. Scheduling, calendars, announcements, and notifications

## 7.1 Scheduling data model

- `schedule_series` captures bounded user intent and recurrence.
- `schedule_occurrence` materializes each occurrence, retaining original time while applying versioned exception state.
- `class_session` is the stable anchor that live classes, attendance, recordings, and downstream exams should reference.
- Schedules carry an IANA timezone. Support finite one-time/daily/weekly/monthly recurrence. Recurrence requires occurrence count or end-local-time and is capped at 1,000 occurrences.
- Nonexistent DST-gap and ambiguous DST-overlap local times must be rejected rather than silently shifted.
- Monthly day-of-month rules clamp to the last valid day in shorter months.

## 7.2 Conflicts and calendar scope

Detect teacher overlap, batch overlap, physical room overlap within a branch, exam overlap, and institutional holidays. Serialize conflict-sensitive writes using a tenant-scoped PostgreSQL advisory transaction lock. An override requires `SCHEDULE_CONFLICT_OVERRIDE`, a non-empty reason, actor, and persisted conflict evidence plus audit event.

Calendar visibility is scope aware: administrators get their authorized tenant/branch calendar; learners and teachers get calendars based on their own active enrollments/teaching assignments, not a tenant-wide curriculum catalogue.

## 7.3 Announcements

Supported targets include organization, branch, course, batch, subject, role, and membership. Support priorities normal/important/urgent/emergency, scheduled publication, expiry, attachments integration, and acknowledgements. Draft/scheduled audience rules remain editable while unpublished. At publication, resolve recipients and persist a recipient snapshot; later enrollment changes must not rewrite history. Scope checks apply to both the existing announcement target and proposed new target, including branch scope.

## 7.4 Durable notifications

Business operations must not synchronously depend on SMTP. Insert business state, notification outbox event, and recipient snapshot in one transaction. Background workers materialize in-app inbox items and optional email deliveries. Use tenant-scoped idempotency, bounded exponential retry, and dead-letter states. Notifications use per-membership/event-type preferences; default enabled unless overridden. Operational queue/backlog visibility requires its own permission.

- Outbox retries are bounded; the architecture documents dead-letter after 12 failures.
- Email delivery retries are separate and bounded; dead-letter after eight SMTP failures.
- If email is disabled, mark email delivery as skipped while in-app notification still works.
- Exactly-once delivery through SMTP is not promised: a remote server may accept mail before a local transaction failure.

## 7.5 Today dashboard

`/api/v1/today` returns the current member’s timezone-aware daily calendar and unread count. Administrative daily totals appear only when the caller has management scope. Personal users must not receive tenant-wide schedules or counts.

## 7.6 Messaging

Support controlled teacher↔student, teacher↔batch, administrator↔user, and support↔tenant communication. Policy may disable student-to-student messaging. The implementation includes tenant-scoped messages/conversations, membership integrity, blocking, moderation/reporting, evidence snapshots, delivery receipts, and policy/rate windows. Keep delivery state and abuse controls auditable, retained under policy, and bounded. The implementation-status audit still calls for further completion of persistent conversation/thread UX, delivery/read state, moderation/reporting/blocking, retention, and abuse-control behavior—do not equate schema/partial APIs with a finished messaging product.

---

# 8. Learning content, uploads, storage, and retention

## 8.1 Versioned resource model

A resource has a stable identity and immutable numbered versions. Each version contains exactly one textual body or one storage object. Resource visibility, release time, expiry, and download policy are evaluated server-side. Provider locators never leak into academic business rules or client-visible objects.

Supported content intent includes text/Markdown/rich text, PDF, office documents, spreadsheets, images, audio, video, archives, source code, URLs, and other explicitly allowed media types. Type-specific support must be verified; do not imply every format is fully previewable simply because upload is possible.

## 8.2 Upload pipeline

`initiate → append chunks at explicit offsets → resume → exact byte-count check → optional SHA-256 check → optional malware scan → stream to configured provider → persist object/resource-version metadata`.

Requirements:

- Disk-backed staging; never read a multi-gigabyte file fully into JVM heap.
- Offsets are monotonic; overlapping/out-of-order chunks are rejected.
- Exact expected length and optional digest must match.
- Server generates object paths/IDs; local filesystem rejects traversal.
- Optional ClamAV INSTREAM scan has explicit states. If no scanner is configured, record `NOT_CONFIGURED`; never falsely claim the file was scanned.
- Rejected malware is terminal `ABORTED` and is never finalized into provider storage.
- If metadata/audit persistence rolls back after remote upload, compensate by deleting remote object; if delete fails, enqueue durable cleanup.
- Download permission is checked before provider stream opening. Provider credentials never appear in resource responses.
- Retry, retention, staging cleanup, and storage cleanup use durable state and bounded batches.

## 8.3 Storage providers and BYOS

Current adapters: local filesystem, S3-compatible storage, Google Drive, and Shared Drive. Architecture must remain open to NAS/NFS/SMB, GCS, Azure Blob, and client-managed storage. Tenant configuration chooses preferred provider(s) independently for documents, temporary processing, backups, and recordings.

Google Drive is a true storage adapter, not a hyperlink wrapper. Prefer resumable uploads and narrow OAuth scopes (e.g. `drive.file` where suitable). Support Shared Drives for institution-owned files. Do not rely on folder names as relational metadata, and do not use Drive as the application database. PostgreSQL must own tenant/resource/recording metadata, size, hash, MIME type, duration, provider, provider object ID, timestamps, retention state, and availability.

## 8.4 Filesystem safety

Use temporary `.part` streams, flush/fsync where appropriate, checksum, and atomic rename. Never disclose filesystem paths. Configure quota, minimum free-space threshold, retention, archive location, and health checks. When space falls below a safe threshold, stop new recording allocations and alert operators while keeping critical academic paths alive.

## 8.5 Recording storage tiers

Recording tiers are `HOT`, `CACHE`, `ARCHIVE`; the current architecture’s default policy stores media hot for 14 days, cache for 30 days, then archives. Master and thumbnail assets migrate together. Migration is copy-then-delete: destination write and metadata-locator update precede old object deletion. On failure, retain the previous usable object and retry. Retention is separate from archive; mark deletion only after retention and deleted-object grace.

---

# 9. Question bank, assessment, and exams

## 9.1 Question bank and versions

`questionbank` owns stable question identity plus immutable versions. Published assessments attach `question_version` IDs, never mutable question content. Editing a question after a student has answered creates a new version and records actor, timestamp, reason, prior/new content, and affected assessments. Authoring/catalog/version reads require assessment-management authority; hiding question-bank UI is not enough.

Initial supported types: `SINGLE_MCQ`, `MULTIPLE_SELECTION`, `TRUE_FALSE`, `NUMERIC`, `FILL_BLANK`, `SHORT_ANSWER`, `LONG_ANSWER`, `ESSAY`, `MATCHING`, `ORDERING`, `FILE_SUBMISSION`. The broader product plan lists future matrix/hotspot/annotation, math, audio/video, programming, SQL, case-study, and compound questions; these are future work unless represented in the current versioned question schema/scoring and test suite.

## 9.2 Assessment lifecycle and construction

Assessment state: `DRAFT → PUBLISHED → CLOSED → ARCHIVED`. A published assessment cannot be changed in place; create another assessment version. Each version records the availability window, duration, number of attempts, maximum marks and navigation settings. Teachers/admins construct exams manually or from question-bank rules.

Full product requirement includes configurable batch availability, duration, attempts, marks/pass criteria, negative marking, question/option shuffle, sections/navigation/backtracking, late submission, answer review, result-release policy, and future blueprint/randomized generation. Every setting implemented in the current API must be validated server-side.

## 9.3 Attempts and server time

- Attempts are tied to an immutable assessment version and assigned batch.
- Persist `started_at`, `expires_at`, `submitted_at`.
- Server time is authoritative; browser clock is display assistance only.
- An attempt must be `IN_PROGRESS` and before expiry for autosave; a client cannot extend expiry by changing its clock.
- Tenant, active member, assignment, state, and availability checks apply to start/read/write/submit paths.
- The response packet is audience-checked and redacted: answer keys must never be sent to learner clients.

## 9.4 Autosave and resilience

- Autosave uses idempotency keys; repeated keys return the committed result rather than duplicating an answer.
- Server sequence/revision is monotonic so stale client writes cannot overwrite newer work.
- Preserve answers through connectivity interruptions; use local/encrypted temporary client state where appropriate, with periodic server persistence.
- Each saved answer is associated with attempt ID, question ID, revision, payload, server sequence and timestamp.
- Retry-safe submission must not create duplicate attempts/submissions. Existing student answers must never disappear due to retries or provider errors.

## 9.5 Exam integrity

Product policy may support attempt limits, passwords, IP/device restrictions, randomization, timing, copy controls, fullscreen warnings, tab-switch telemetry, and a proctoring adapter. Browser controls are deterrents/signals, not proof that cheating is impossible. Exams must be accessible and fair.

---

# 10. Grading, results, academic review, and regrading

## 10.1 Immutable grading history

Submitted attempts generate append-only grade revisions containing source, actor, scores, rationale, and immutable grade-item snapshots. Publishing a newer revision supersedes an earlier published revision without editing past history. Final results must be reproducible from the exact assessment/question version and grading revision.

Objective grading supports the current assessment question types. Unanswered objective/text questions receive zero rather than a negative penalty; answered incorrect responses may incur configured negative marks. File submissions are manually graded. Teacher overrides create a new revision and preserve both prior system score and teacher score.

Keep `SYSTEM_SCORE`, `TEACHER_SCORE`, and `FINAL_SCORE` separate. Do not overwrite automatic grading evidence when a teacher intervenes.

## 10.2 Manual grading

Support question-by-question or learner-by-learner workspaces, rubrics, rubric item comments, feedback, anonymous grading where permitted, double grading, batch grading, and moderation as the roadmap matures. Grade publication requires permission and audit history.

## 10.3 Academic review/challenge

A learner may challenge an entire grade or a specific answer for reasons such as incorrect key, alternative method, ambiguity, multiple valid answers, manual-grading error, calculation discrepancy, or missing information. Workflow: challenge → scoped review → discussion → optional evaluator/moderator → decision/resolution. Persist evidence and state transitions.

- Review comments/discussion are tenant scoped.
- Proposed answer revisions are immutable revisions.
- Answer IDs are validated against the reviewed attempt.
- Review managers must be scoped to the learner/batch or other explicit authority.
- Decision and resolution paths enforce the same scope boundary.
- Project score impact before applying a revision.
- Regrading is a new revision/event, never an in-place rewrite.
- Publish history and notify affected students with a reason.

Completion requirements still include stronger pass/fail handling, anonymous/double grading, moderation/final adjudication, immutable reproducibility, and review-driven regrading tests across edge cases.

## 10.4 Explanations and appeal evidence

Question authors may supply why the correct answer is correct, why distractors are wrong, alternative accepted methodologies, and references. Release explanations only when exam policy permits. Store answer key and scoring rule versions so the decision can be reproduced.

---

# 11. Assignments, projects, and gradebook

## 11.1 Assignment lifecycle

Assignments are tenant-owned work items optionally attached to multiple batches. A published assignment cannot be mutated back to draft. Learner catalog exposes published and closed work while hiding draft/archived items. A draft submission must enforce the same published-status and active-batch eligibility as a final submission.

Support text/file/link/audio/video/source-code work, drafts, deadlines, late penalties, multiple submissions, resubmission, group work, project milestones, rubrics, feedback, and peer review as the relevant surfaces are completed. Submission attempts are immutable once submitted; resubmission creates a new attempt number. Record lateness explicitly rather than rewriting the original deadline.

## 11.2 Weights and grade formula

Weights use integer basis points (0–10,000); assigned weights within a batch must not exceed 100%. Formula versions must be retained. The product plan asks for exams, assignments, projects, practicals, participation/manual scores and formulas such as weighted percentages, best-of-N, drop-lowest, extra credit, minimum component marks, pass conditions, and rounding. The current gradebook calculation supports deterministic drop-lowest, best-of-N, extra-credit percentage points, minimum completed components, weight renormalization after exclusions, configurable rounding, and validation of scores/max points/weights; default behavior preserves standard calculation.

Gradebook consumes `AssignmentDirectory` and does not directly use assignment repositories. The latest submission/published score is used according to the selected policy; formula and data scope must be auditable.

## 11.3 Gradebook security

- Student reads are self-only.
- Learner must be enrolled in the target batch.
- Staff access to another learner requires the target batch to be within the caller’s active assigned batch or a legitimate branch-scoped permission.
- Organization Owner/Administrator/Academic Administrator or other explicit cross-scope authority is limited by its documented tenant/branch scope.
- Guardian access to a learner must require a persisted, active, verified guardian-to-learner relationship. The current backend model has no such persisted relation; therefore Guardian cross-learner gradebook requests fail closed until a relationship model and authorization/tests are implemented.
- The local browser demo’s linked learner data is synthetic and cannot substitute for backend relationship evidence.

---

# 12. Live classrooms, presence, and attendance

## 12.1 Live-class provider boundary

Use a provider-neutral live-class control plane with LiveKit as the initial media implementation. Keep API keys/secrets on the server. The browser receives short-lived participant JWTs only. Participant identity should be an opaque membership-derived value, not PII. Do not persist LiveKit room IDs as the canonical classroom identity.

Required experience includes audio/video, screen/presentation share, teacher/co-host controls, hand raising, chat, Q&A, polls, whiteboard hooks, participant list, mute/removal, waiting room, recording controls, network-quality indicator, low-bandwidth mode, reconnect, and robust start/end/join/leave lifecycle.

Permissions include `LIVE_CLASS_VIEW`, `LIVE_CLASS_MANAGE`, `LIVE_CLASS_MODERATE`, `LIVE_CLASS_CHAT`, `PRESENCE_VIEW/MANAGE`, and `ATTENDANCE_VIEW/MANAGE`. Normal sessions should use adaptive stream/dynacast where supported. Low-bandwidth profiles reduce capture/video and prioritize audio/slides.

## 12.2 Presence evidence

Record server-side `JOIN`, `LEAVE`, `DISCONNECT`, and `RECONNECT` intervals/heartbeats. Presence is not the same as attention. Expire stale heartbeats and handle reconnection without double counting. Derive total session duration, connected duration, late join, early leave and attendance percentage from stored evidence.

## 12.3 Attendance policy

Support `DISABLED`, `MANUAL`, `AUTOMATIC`, `AUTOMATIC_WITH_OVERRIDE`, and `HYBRID`. Finalize attendance on the server from accumulated evidence, not a single client event. Manual override stores old status, new status, reason, actor and timestamp; audit every override.

## 12.4 Attendance integrations — gap

Design adapter boundaries for manual entry, rotating QR, NFC, RFID, external biometrics, kiosks, import/API, and offline sync. Normalize observations; handle replay/idempotency, offline conflicts, source trust, privacy and authorization. The implementation status explicitly lists kiosk/NFC/RFID/biometric integrations, offline sync, replay/idempotency and conflict resolution as work requiring continued completion/audit.

---

# 13. Recording platform

## 13.1 Control plane and lifecycle

A recording belongs to a tenant, live classroom, and stable `class_session`. Management-authorized user requests a preset while class is live. Request is persisted as `REQUESTED`; a scheduled worker starts/reconciles capture independently of HTTP request duration.

States: `REQUESTED`, `STARTING`, `RECORDING`, `FINALIZING`, `READY`, `ARCHIVING`, `ARCHIVED`, `FAILED`, `DELETED`. Reconciliation polls provider state. Provider failure is durable with bounded safe error text and retry/recovery behavior.

## 13.2 Capture, processing, storage

- `RecordingMediaProvider` isolates LiveKit Egress from the domain model.
- The current provider captures composite MP4; Egress and app worker share a configured processing volume.
- Worker hashes, records size/duration, persists metadata, optionally uses `ffmpeg` for thumbnail, and finalizes through `StorageAdapter`.
- Thumbnail failure must not invalidate a successfully stored master.
- `ECONOMY` and `BALANCED`: H.264 720p/30; `HIGH_QUALITY`: H.264 1080p/30 where viable; `SOURCE_ARCHIVE` must not claim untouched source-track capture if provider implementation only stores a composite.
- Recording policy supports never, teacher choice, automatic, or selected courses; resolution, retention, and storage are tenant controlled.

## 13.3 Playback and access

- Never return a raw storage locator as playback authority.
- Require tenant ownership, READY state, member identity, `RECORDINGS_VIEW`, and active-batch authorization unless a valid management scope applies.
- Playback token is short-lived (the architecture documents five minutes), HMAC signed and bound to recording and membership.
- Stream opening rechecks current scope; a token issued before enrollment revocation cannot preserve later access.
- Web player rotates a visible identity watermark. This is a deterrent, not DRM/absolute screen-capture prevention.
- Audit playback-sensitive actions and enforce download policy.

## 13.4 Retention and recovery

Worker intervals configurable via `platform.recording.*`. Workers establish tenant execution context. Storage policy changes are audited/versioned. Archive migration is copy-then-delete. Keep previous object usable on failure. Deletion has grace period and durable state. Production qualification remains needed for worker crash safety, retry/recovery, transcoding ladder, CDN/distribution, archive migration, and storage restoration.

---

# 14. Finance, subscriptions, entitlements, usage, and white-labeling

## 14.1 Keep two billing domains separate

- **Institution finance/student fees:** fees, installments, invoices, payments, receipts, refunds/reconciliation.
- **UEP commercial subscription:** tenant subscription, plan catalog, feature entitlements, hard/soft limits, usage metering, trials, grace, suspension, expiration, and commercial distribution.

Never conflate an institution’s student fee with a UEP SaaS subscription.

## 14.2 Institution finance

- Monetary values use decimal DB precision and ISO-4217 alpha currency code.
- Fees are tenant scoped. Invoices have `DRAFT`, `ISSUED`, `PARTIALLY_PAID`, `PAID`, `VOID`, `OVERDUE` states.
- Installments sum exactly to invoice total.
- Payment amounts are positive and at most two decimal places; reject sub-cent values instead of silently rounding.
- Capture changes invoice transactionally and creates immutable receipt.
- Lock invoice row before checking balance so concurrent settlements cannot overspend.
- Reused provider settlement reference is prohibited within tenant/provider scope.
- Current `MANUAL` provider records operator-attested settlement and **does not call an online gateway**. It requires authorized finance user, external settlement reference, and explicit confirmation that funds have settled.
- Manual refunds are not claimed successful; they need separate reconciliation. Real payment gateways require authorization/capture, signed webhooks, idempotency, refunds, provider decline/timeout behavior, and reconciliation qualification.

## 14.3 Commercial entitlement model

Runtime decision chain: `Tenant → Subscription → Entitlement → Limit → Usage → Decision`. Plan name/catalog is not a runtime authorization switch. No `if (plan == PRO)` logic. Entitlement API is the boundary.

Subscription states: `TRIAL → ACTIVE → GRACE → SUSPENDED → EXPIRED`, with explicit `REVOKED` and `TERMINATED` control states. Expiry must not immediately destroy customer data; grace/read-only restrictions are explicit policy.

Feature keys in the commercial architecture include `LIVE_CLASS`, `RECORDING`, `ASSESSMENTS`, `AUTO_GRADING`, `ADVANCED_REVIEW`, `ASSIGNMENTS`, `PAYMENTS`, `CERTIFICATES`, `API`, `WHITE_LABEL`, `SSO`, `AI`. Limits include active students/teachers/admins, storage bytes, recording minutes, concurrent meetings, meeting participants, SMS messages and AI units. Domain authorization and entitlements are independent checks, both required.

## 14.4 Metering and quotas

Use tenant/limit/period unique keys, pessimistic row locking when usage exists, and daily usage periods where configured. Return consumed amount and hard limit without exposing payment-provider detail.

## 14.5 White-labeling

Tenant scope includes brand name, validated six-digit primary color, logo/favicon URLs, optional custom domain, enabled state, and optimistic version. A stored custom domain is not proof of DNS ownership or TLS provisioning; those remain deployment concerns. Avoid tenant-specific source forks.

## 14.6 Signed offline license — planned capability

The future `SIGNED_OFFLINE_MANIFEST` authority uses Ed25519 envelopes (`keyId`, exact payload bytes, signature) verified before payload trust. Payload includes license/customer binding, monotonically increasing revision, issued/not-before/expiry/offline-grace times, deployment scope, entitlements, limits, add-ons, version compatibility, and revocation/refresh metadata. Private signing keys never ship with the app. Anti-rollback must refuse a lower accepted revision unless an explicit privileged recovery is used. Do not imply offline licensing is fully qualified merely because the format is documented.

---

# 15. Analytics, operations, search, and workflow automation

## 15.1 Analytics source of truth

Analytics is read-only and derives from authoritative transactional data—no second mutable source of academic, finance, recording, or licensing truth. Tenant and permissions must be enforced in each query.

Current API scope includes attendance distribution/rate; assessment attempts/submissions/published grades/pass counts/average score; question quality from published grade items; recording count/duration/bytes/processing; finance totals only with `FINANCE_VIEW`; entitlement usage/limits; operational audit/notification/dead-letter/session counters; 30-day linear storage forecast; and bounded CSV exports for attendance, assessments, recordings, finance.

Date ranges are limited to 366 days. Finance omitted from general overview unless authorized. Operations counters require `OPERATIONS_VIEW`, not just analytics. Branch-scoped administrative access must not reveal tenant-wide analytics before branch-filtered aggregation is implemented. Storage forecast is an observed preceding-30-day linear projection, not a billing commitment or probabilistic forecast.

Large scheduled exports and object-storage delivery are future work. Use cursors/streamed writing for future large exports; do not materialize huge CSV/XLSX/JSON blobs in heap.

## 15.2 Performance and query constraints

- Paginate every unbounded collection; apply database-side pagination before mapping.
- Eliminate N+1 query patterns and add query budgets for high-volume endpoints.
- Use tenant-leading indexes for high-frequency joins and date/range scans.
- Use bounded SQL aggregations for the initial analytics model. Introduce warehouse/materialized data only when scale or reporting latency proves the need.

## 15.3 Search — completion gap

The master plan’s initial baseline is PostgreSQL full-text search over courses, resources, questions, authorized students, announcements and recording transcript metadata. Access-filtering, indexing/reindexing, rate limits, scale and global search are explicitly listed as further completion/audit work.

## 15.4 Workflow engine — later milestone

Roadmap describes `WHEN event IF conditions THEN actions`, e.g. low attendance triggers learner/mentor notification, or low score assigns remedial content. A real engine needs versioned configurable states/transitions, approval rules, deadlines, escalation, conditional action, bounded retries/idempotency and historically reproducible decisions. Do not treat the roadmap example as an already-complete configurable workflow runtime.

---

# 16. Advanced academics and engagement

## 16.1 Outcome and competency tracking

Create learning outcomes and competencies, map them to course/assessment/assignment/class or other explicit academic targets, and record evidence-bearing/versioned mastery per membership/competency. Mastery complements—it never replaces—authoritative grades, attendance, assignments, or content records. Maintain tenant/domain coherence constraints.

## 16.2 Learning paths

Reusable ordered learning journeys with items and per-membership progress. Preserve links to authoritative underlying resources/assessments; do not create a parallel source of academic truth.

## 16.3 Credentials

Tenant-scoped credential templates and issued credentials, immutable unique verification codes, canonical verification URL and QR payload, issuance/revocation audit, privacy-minimal public verification, and no exposure of OIDC subject or unrelated private information. Portable signing/issuer identity/status/revocation and Open Badges/CLR compatibility remain further work unless expressly qualified.

## 16.4 Mentoring, surveys, feedback

Mentoring is a lifecycle state machine (`REQUESTED`, `ACTIVE`, `PAUSED`, `COMPLETED`, `DECLINED`) with tenant-scoped mentor/mentee memberships, goal, version and audit. Surveys have first-class questions, bounded JSON answer payloads, optional anonymity, opening/closing window, and one identified response per membership. Anonymous responses must not persist identity linkage. Feedback targets typed domain records, ratings are 1–5, content is audited, and access is scoped.

**Relationship warning:** an active mentorship is not automatically a Guardian relationship. Guardian→learner access must be separately and explicitly modeled, verified, scoped, revocable, and tested.

---

# 17. Integration and interoperability

## 17.1 Public API credentials

Tenant-local, scoped, hashed, revocable, expiring credentials. Current membership permissions intersect explicit credential scopes. No empty-scope implicit broad access. Usage tracking must not conflict with optimistic versioning.

## 17.2 Signed webhooks

- Durable event/outbox before network transport.
- Headers `X-UEP-Event-ID`, `X-UEP-Event`, `X-UEP-Signature`; timestamped HMAC signature.
- Encrypted signing secrets, event/delivery history, bounded exponential retry, idempotency.
- Validate endpoint DNS/IP at configuration and immediately before send. Reject loopback, private, link-local, reserved, and mixed public/non-public destinations; disable redirects.
- DNS validation is not DNS pinning. Production still requires egress firewall/network policy to block private networks and cloud metadata.
- Do not persist raw exception messages or callback query parameters; store safe categories/status. Restore interrupt flag on interruption.
- Outbox processing and delivery failures must be tested under worker restart/failure, not only happy path.

## 17.3 OIDC federation

Tenant identity providers are relying-party configuration. Authorization URL uses state and nonce. Final authentication, token validation and account linking remain under the trusted identity provider. UEP must not become a second identity store. Multiple-provider lifecycle, claim/group mapping, deprovisioning, and security qualification remain further completion work. OpenID Federation trust discovery is future work.

## 17.4 OneRoster and QTI

- OneRoster adapter exports a bounded tenant-canonical subset of OneRoster 1.2 CSV and validates CSV envelope for future import.
- QTI adapter currently handles a safe baseline `assessmentItem` XML representation; disable external entities/DTD. Do not claim full QTI 3.0 assessment-test/response-processing conformance from this foundation.
- Standards schemas are adapters around UEP’s canonical domain; do not copy external schemas into core internal tables.
- Future interoperability includes LTI, CASE, xAPI, Caliper, Open Badges, CLR and SCORM; these are roadmap scope, not necessarily currently qualified.

## 17.5 External storage and notification providers

Storage binding chooses configured adapter/provider without changing content/recording entity semantics. Notification provider configuration uses explicit key/type and isolates HTTP transport from core outbox. Provider errors do not leak secrets or cause unrelated business transactions to fail.

---

# 18. UI, UX, accessibility, and the synthetic demo

## 18.1 Role-based real application

Keep a coherent design system but role-specific workspaces:

- **Student:** Today, Learn, classes, assignments/tasks, exams, results, attendance, recordings, messages, calendar.
- **Teacher/Evaluator:** Today, assigned classes/learners, content, assessment, submissions, grading, challenges/reviews, communication, analytics within authorized scope.
- **Administrator:** tenant structure, people/roles, academics, schedules, announcements, policy, operational reporting, finance/licensing per permission.
- **Guardian:** explicitly linked learner only, read-only academic visibility unless specifically allowed; never infer link from tenant/cohort.
- **Finance:** fees/invoices/payments/reconciliation according to permissions.
- **Support/Auditor/Developer:** purpose-limited access, no general academic learner data unless an explicit authorized support workflow exists.

A Universal Today view is the primary landing surface. Browser composes APIs for display; schedule, tenant, counts and notification visibility remain server authoritative.

## 18.2 Synthetic local demo

The local-demo mode is a seeded presentation scenario. It must be discoverable and interactive, but must never be mistaken for production security/authentication. Its persistence and changes should be explicit, cross-role propagation should follow relationships, and unauthorized data must stay hidden. Failed browser storage writes must not broadcast a false success; show a warning when a value is only retained in the current tab.

The demo acceptance scenarios include: student submits work→teacher grades→student sees grade; teacher updates progress/attendance→student sees it; client changes organization data→administrator sees it while other tenants do not; admin course approval affects only that course; developer release telemetry remains separate from academic records; Guardian sees linked learner only. Browser refresh should not revert persisted demo changes.

## 18.3 Accessibility and responsive behavior

WCAG 2.2 AA is a release requirement: keyboard operation, screen-reader semantics, visible focus, adequate target size, captions, contrast, zoom, reduced motion, accessible examinations and graphs. Work on desktop/laptop/tablet/mobile browsers with task-appropriate layouts; do not squeeze desktop admin workflows onto narrow screens indiscriminately.

## 18.4 PWA and offline mode

PWA should support installable app shell, notifications, limited offline metadata and cached resources. Exams cannot treat offline/client state as authoritative until the dedicated synchronization/conflict subsystem is implemented. Native sync needs versioned contracts and replay/idempotency.

## 18.5 Frontend performance

Route-level code splitting, lazy heavy editors/media, virtualized large tables, optimized images, request deduplication, server-state cache and purposeful prefetching. Avoid downloading the exam builder/video player/admin analytics/rich editor to every student login route.

---

# 19. Security, privacy, data integrity, and audit

## 19.1 Minimum baseline

- TLS in production; secure cookies/session policy; CSRF/CSP/XSS protections; rate limiting.
- OIDC, MFA/passkeys through the identity provider, short-lived auth, session revocation.
- Strict tenant isolation and branch/resource scope.
- Server-side RBAC and commercial entitlement checks; no frontend-only authority.
- Secret manager/container secret store for production. No plaintext Google refresh tokens, payment secrets, media keys, offline-license private keys, or other credentials in config/code/logs.
- Production-secret guard blocks missing, blank, weak or known dev secrets. `PLATFORM_DEV_MODE=true` is development-only.
- Signed releases, dependency scanning, SBOM, vulnerability review, backup encryption and recovery keys.
- Database constraints for high-impact invariants; append/revision history for academic/financial facts.

## 19.2 Data/model constraints

Tenant-owned tables carry `tenant_id`; relationships use composite `(tenant_id, id)` foreign keys. Add uniqueness/check constraints to express one-target enrollment, active duplicate prevention, valid status enums, valid score/weight ranges, one attempt number per user/version, unique answer/idempotency keys, object integrity before AVAILABLE, and no duplicate provider settlement reference.

Use optimistic `version` on editable high-value entities. Use row/advisory locks for contended count/balance/conflict invariants. Database constraints are necessary; service checks alone are insufficient.

## 19.3 File and URL security

File controls include type/size validation, extension-independent MIME detection where appropriate, malware scanning, archive-bomb protection, filename normalization, digest, quarantine, traversal safety, and download authorization every time. Uploaded content never executes in the application context.

External network fetch/send endpoints (webhooks, provider endpoints, imports) need SSRF safeguards, destination validation, redirect control, request/time limits, safe error handling, and production egress control. XML parsing disables external entities and DTDs.

## 19.4 Exam and recording privacy

Answer keys are never included in student packets. Server validates audience and state. Recording protection layers are authorization, short-lived tokens, signed request, download policy, moving watermark and audit. Do not advertise absolute screen-capture prevention. PII exposure in public credential verification is minimal.

## 19.5 Audit and logs

High-value events include grade/result changes, answer-key edits, overrides, permission changes, payment/refund/settlement, recording access, support access, and licensing changes. Audit should capture actor, tenant, action, resource, reason, old/new value references, timestamp and useful trace/session metadata while avoiding sensitive payloads.

Never log passwords, OAuth tokens, exam answers, full payment data, private recording URLs, sensitive uploaded documents, callback query parameters, or provider credentials. Use structured logs and OpenTelemetry-compatible metrics/traces/logs.

---

# 20. Reliability, performance, operations, and cost

## 20.1 Resilience rules

Use durable background jobs for notifications/email, recording processing, archive/storage migration, thumbnail/transcoding, certificates, exports/imports, analytics tasks, regrading and retention cleanup. API requests should not block for these long-running jobs.

Use transactional outbox for side effects that must follow successful database transactions. All retried external operations require idempotency: exam submission, payments, webhooks, notifications, credential issuance, upload finalize, imports and automatic regrade.

Graceful degradation: email may fail without blocking result publication; analytics may fail without blocking classes/exams; recording failure must not kill live class; transcription failure must not kill recording.

## 20.2 Performance budgets (targets, not assertions)

Master-plan initial engineering budgets:

- ordinary cached API p95 <100 ms;
- ordinary DB API p95 <300 ms;
- answer autosave p95 <300 ms;
- notification enqueue <200 ms;
- perceived simple page interaction <100 ms.

0.15 qualification guide also defines its own smoke/load gate: the dependency-free HTTP probe uses configured thresholds (guide states p95 below 750 ms and p99 below 1500 ms under the included 20 req/s arrival profile), plus packaged-server startup and 30 concurrent health requests. These are different scopes. Do not replace production-like endpoint/load evidence with a health-only check or claim CI-runner measurements are production SLOs.

## 20.3 Scale/load targets

Use realistic bursts: thousands of logins/meeting joins/exam starts/submissions. Do not benchmark only `/health`. Measure p50/p95/p99, throughput, DB query latency, meeting join time, autosave latency, reconnect success, CPU/RAM per user, bandwidth per meeting, recording storage per hour, queries/request and cost per active learner. Benchmark claims must be reproducible and list hardware, data, environment, build, concurrency and profile.

## 20.4 Boundaries and resource safety

- Bound connection pools, timeouts, retries, queues, caches, exports, recurrence materialization, and request sizes.
- Stream large files and exports; never keep recordings in heap.
- Use virtual threads only for suitable blocking I/O; profile with JFR and verify under load.
- Keep hot data indexed and high-volume tables append-friendly. Partition later when measurements justify it, not everywhere on day one.
- Use per-tenant deduplication only by default; global cross-tenant content dedup complicates deletion/isolation semantics.

## 20.5 Storage/media cost model

Major cost centers are compute, database, video bandwidth, recording/transcoding, storage, SMS/email, and optional AI. Keep compact installations on Compose/VMs; avoid Kubernetes without a demonstrated need. BYOS, 720p defaults, only-record-when-needed policy, archive tiers, tenant-owned SMTP/SMS/payment accounts, and demand-scaled recording workers reduce operational cost. Analytics stays in PostgreSQL until evidence justifies a warehouse.

## 20.6 Backup and recovery

Production strategy should include PostgreSQL continuous backup/PITR, daily snapshots, object versions, configuration backups, and key/credential recovery. Suggested mature SaaS target is RPO ≤5 minutes and RTO ≤1 hour; actual targets must be chosen and measured per deployment/contract.

`scripts/backup-restore-drill.sh`:

- Requires `DATABASE_URL`, `BACKUP_FILE`, `RESTORE_DATABASE`, and `RESTORE_TARGET_IS_DISPOSABLE=true`.
- Requires `pg_dump`, `pg_restore`, `psql`.
- Refuses source/target identity collision and existing backup path, writes restrictive file permissions, dumps custom format, validates archive, restores with `--exit-on-error --clean --if-exists`, and verifies tenant/membership/Flyway counts.
- Must only run against an explicitly disposable restore target. Never point a destructive restore at production.

Backup tooling existing is not equivalent to a successful production-like restore. Production GA still requires restore evidence, measured RPO/RTO, object-storage recovery and incident/runbook exercises.

---

# 21. Implementation roadmap and release gates

The master plan uses four-component versioning: `stable.major.minor.patch`; the current canonical version is `0.15.0.0-SNAPSHOT`.

| Milestone | Theme | Core exit criteria |
|---|---|---|
| 0.1.0.0 | Engineering foundation | Monorepo, Java/Spring/Modulith, Postgres/migrations, React/TS/OpenAPI, auth/tenant/audit/licensing skeleton, CI, coding standard, observability and dev environment; app boots, isolation tests and clean CI. |
| 0.2.0.0 | Identity & organization | Users, memberships, roles/permissions/custom roles, branches/settings, administrator UI, MFA/passkey IdP integration, session management/revocation. |
| 0.3.0.0 | Academic core | Period/program/course/subject/module/batch, enrollments, teaching assignments, capacity concurrency, learner/teacher/admin dashboards. |
| 0.4.0.0 | Scheduling & communication | Calendar, finite recurring schedules, conflicts/exceptions, classes, announcements, notification outbox/email/preferences, Today. |
| 0.5.0.0 | Learning content | Notes/resources, immutable versions, resumable uploads, storage adapters, download permissions, retention and cleanup. |
| 0.6.0.0 | Assessment | Question bank/versioning, question types, exam construction/assignment, attempts, autosave/idempotency, server timing, submission. |
| 0.7.0.0 | Grading/review | Objective/manual grading, rubrics/explanations, publication, immutable grades, challenge/discussion, answer revision, impact analysis and regrading. |
| 0.8.0.0 | Assignments/gradebook | Projects/submissions, deadlines/resubmissions/rubrics, gradebook, deterministic formulas, progress and access scope. |
| 0.9.0.0 | Live learning | Media provider abstraction, LiveKit, short-lived tokens, moderation/screen share/chat, presence and attendance, low-bandwidth recovery. |
| 0.10.0.0 | Recording | Capture worker, storage tiers, retention, playback auth/watermark, quality presets, integrity and archive. |
| 0.11.0.0 | Finance/commercialization | Fees/installments/invoices/receipts, payment adapter, subscriptions, entitlements, usage/quotas, trials/grace, white-label. |
| 0.12.0.0 | Analytics/operations | Analytics/reporting/export, operational dashboards, usage and storage forecast. |
| 0.13.0.0 | Advanced academics | Outcomes, competencies, learning paths, credentials/verifications, mentoring, surveys, feedback. |
| 0.14.0.0 | Integrations | API credentials, webhooks, federation foundation, OneRoster/QTI adapters, external storage/notification providers. |
| 0.15.0.0 | Reliability/performance qualification | No major feature expansion; load profiles, DB/JVM/browser/media tuning, backup/restore, failure/chaos tests, penetration testing, accessibility, migrations and tenant leakage verification. |
| 0.16.0.0 | Production candidate | Admin/teacher/student guides, installation/security/privacy manuals, SLA instrumentation/status page, release/update and deployment automation, billing readiness. |
| 1.0.0.0 | Production Web GA | All P0/P1 integrated; no critical known defects; security/performance/accessibility, tenant isolation, backup/restore, reproducible assessment history, recording/storage recovery, payment reconciliation, entitlement enforcement and documentation verified. |
| 1.1.x.x | Native protocol stabilization | Stable common contracts for desktop/mobile. |
| 1.2.x.x | Windows | Native production client. |
| 1.3.x.x | macOS | Native production client. |
| 1.4.x.x | Linux | Native production client. |
| 1.5.x.x | Android | Native production client. |
| 1.6.x.x | iOS/iPadOS | Native production client. |

Native applications are first-class product scope but not present as complete production app source trees in this repository snapshot. Preserve contracts now; do not prematurely freeze desktop technology.

---

# 22. Current implementation state and outstanding gaps

## 22.1 Snapshot evidence

The live `main` tree reviewed for this document is `651b09ae49371e001690e6a734883ff6662741dd`. GitHub Actions run `40703082161` for that exact SHA completed successfully across:

- `backend` — Maven verification, migration/application test suite and packaged-server smoke;
- `web` — dependency install, lint, demo tests and production build;
- `repository-policy` — source structure, contract parity, repo/secret checks, backup/restore safety, load-smoke test and `qualify-0.15.py`;
- `vulnerability-scan` — Java SBOM and OSV scan.

That is confirmed repository CI evidence, not a local checkout build or production deployment. It does not by itself satisfy 0.16 or 1.0 release gates.

## 22.2 Notable current fixes already present in reviewed main

- Student role can take assigned assessments; Guardian permissions are separated/read-only at role level. `V044__student_assessment_take_guardian_read_only.sql` makes the role correction apply to existing tenants; V044 is the next version after occupied V001–V043. Flyway version uniqueness is guarded by qualification.
- Assessment catalogue/version visibility is constrained to published assessments assigned to the member’s active batch scope. Question-bank catalog/version reads require `ASSESSMENTS_MANAGE`, including UI request/render gating.
- Assignment draft saves share the same published-status/active-batch guard as final submission. Learner listing filters to published/closed and excludes draft/archived with database-side pagination.
- Academic-review answer revision decisions enforce the same review/batch-scope guard as review resolution.
- Gradebook validates the caller and target learner: student self-only; staff target batch or branch-scope; academic admins use authorized role scope. Guardian cross-learner gradebook requests fail closed because no persisted Guardian relationship model exists yet.
- Recording detail/playback/stream share active-batch scope; stream rechecks current scope after token issuance to block access after revocation.
- Webhook outbox and delivery workers expose configurable initial delay; the integration test delays both schedulers to avoid a startup worker racing manual fan-out/cleanup.
- The gradebook formula supports deterministic policy fields listed in §11.2.
- Demo store handles storage failures more explicitly; browser-demo metrics follow published course visibility and relationship scope.

## 22.3 Specification-derived work that remains open

The current implementation-status document explicitly calls for continued completion/audit of:

1. **Communication:** persistent conversations/threads UX, delivery/read states, moderation, reports/blocking, retention and abuse controls.
2. **Advanced review:** pass/fail, anonymous/double grading, moderation/adjudication, immutable reproduction and comprehensive review-driven regrading.
3. **Attendance integrations:** kiosk/NFC/RFID/biometric adapters, imports/APIs, offline synchronization, replay/idempotency and conflict resolution.
4. **Workflow engine:** real versioned state machine, transitions/approvals, deadlines/escalation, conditional actions, retries/idempotency and reproducible history.
5. **Search:** global permission-aware/tenant-safe search, indexing/reindexing, rate limiting and scale.
6. **Finance:** discounts/scholarships/credits, payment allocation, settlement/reconciliation edge cases, accounting exports and real provider qualification.
7. **Federation:** multiple providers, provider lifecycle, claims/groups mapping, deprovisioning and security qualification.
8. **Interoperability:** LTI, CASE, xAPI, Caliper, mature OneRoster/QTI, Open Badges and CLR-compatible exchange.
9. **Credential portability:** signing, issuer identity, status/revocation and privacy-preserving verification.
10. **White-labeling:** full tenant branding/templates/tenant-specific communications/support; DNS/TLS custom-domain automation is separate.
11. **Live classroom:** reconnect/recovery, stale heartbeat expiry, browser/media failures, large-class/poor-network qualification, provider failure handling and observability.
12. **Recording:** crash-safe workers, retries, recovery, transcoding ladder, CDN/distribution, archive migration and storage recovery.
13. **Analytics:** longitudinal learner/cohort/competency progression, interventions/remediation and safe aggregation at scale.
14. **PWA/offline:** installability, service worker, caching, push and safe offline sync.
15. **Native clients:** Windows/macOS/Linux/Android/iOS/iPadOS and institutional kiosk integrations through stabilized shared contracts.
16. **Production qualification:** authenticated abuse/fuzz tests, authz-bypass tests, upload/storage security, session/token abuse, webhook replay/forgery, federation/payment abuse, privacy authorization, external penetration/legal review, production-like p50/p95/p99 results, WCAG 2.2 AA, PostgreSQL PITR/full restore, object-store recovery, measured RPO/RTO, failure injection, provider degradation/failover, migration/upgrade/rollback testing, and operational readiness.

## 22.4 Relationship access caution

Product/demo specs say Guardians should see only the explicitly linked learner. The backend gradebook currently has **no persisted guardian→learner relationship to verify**, so it denies cross-learner reads. To finish correctly, implement a first-class tenant-scoped, lifecycle-aware relationship with authorized creation/revocation, consent/verification semantics as appropriate, audit, list/read APIs, query scope integration, and tests for positive, negative, cross-tenant, inactive, revoked and ambiguous links. Do not “fix” this by trusting request-supplied IDs or demo state.

## 22.5 Documentation synchronization debt

Some canonical docs have historical CI run counts/commit references that are older than the current live head. `docs/IMPLEMENTATION_STATUS.md` has a confirmed run reference to `8af6a258…`; a later live-main CI run `40703082161` passed on `651b09ae…`. Read live Actions for final evidence. After a substantive implementation decision, update current status and implementation plan/architecture notes so status does not lag code. The older Cloudflare demo guide also contains a branch name from an earlier presentation stage; confirm its deployment instructions against current `main` before using it.

---

# 23. API contract index

The canonical contract is [`docs/api/openapi.yaml`](https://github.com/shreyashjagtap157/UEP/blob/main/docs/api/openapi.yaml). It is OpenAPI 3.1, title “Universal Education Platform API”, version `0.15.0.0-SNAPSHOT`; the reviewed file has 174 path entries. Do not retype schemas from memory. Use `scripts/verify-contract-parity.py` to keep contract and implementation aligned.

The current OpenAPI contract covers these documented groups (exact methods, parameters, schemas, response codes and security belong to OpenAPI). **Important live-source discrepancy:** the backend currently has `AssignmentController` routes under `/api/v1/assignments`, `/api/v1/assignment-submissions`, and `GradebookController` routes under `/api/v1/gradebook`, but these assignment and gradebook paths are absent from the 174-path OpenAPI document at the reviewed head. This is a specification/contract parity gap to close—not a reason to omit those implemented APIs. The current `scripts/verify-contract-parity.py` checks permission and notification-event enums, but does not prove route-operation parity.

- `/platform/version`, `/me`.
- `/identity/bootstrap-owner`, `/identity/memberships`, `/identity/roles`, `/identity/roles/assignments`, `/identity/sessions`.
- `/organization/branches`, `/organization/settings`.
- `/licensing/entitlements/{feature}/decision`, `/commercial/subscription`, trial/grace/suspend, `/commercial/white-label`.
- `/academic-periods`, `/programs`, `/courses`, `/subjects`, `/modules`, `/batches`, `/enrollments`, `/teacher-assignments`.
- `/schedule/series`, `/schedule/occurrences`, conflicts, occurrence/series reschedule/cancel.
- `/announcements`, `/announcements/me`, publish/cancel/acknowledge; `/notifications`, unread count/read/preferences/operations; `/today`.
- `/uploads`, upload content append/complete; `/resources`, text/file resources, versions/download; `/content-retention`.
- `/questions`, question versions; `/assessments`, assessment versions, immutable packet, attached question versions, publish, batch assignment, attempts.
- `/attempts`, answer autosave, submit, grade, grade-history, grade revision publish, grade-item override.
- `/attempts/{attemptId}/reviews`, review comments/discussion, impact analysis, answer revisions, decide, resolve and regrade.
- **Controller routes currently missing from OpenAPI:** `GET/POST /assignments`; `PUT /assignments/{id}`; `POST /assignments/{id}/status`; `POST /assignments/{id}/batches/{batchId}`; `PUT /assignments/{id}/rubric`; `PUT/POST /assignments/{id}/my-submission` (draft/final submit); `GET /assignments/my-submissions`; `GET /assignments/{id}/submissions`; `PUT /assignment-submissions/{id}/grade`; `GET /gradebook/me/{batchId}`; and `GET /gradebook/{batchId}/learners/{membershipId}`. Add complete OpenAPI schemas, permission/error responses, and contract-parity tests for each.
- `/live-classes`, start/end/join/leave/heartbeat/participants/mute/kick, attendance read/finalize/override.
- `/recordings`, live-class recording request, state/stop/playback/stream, `/recording-storage-policy`.
- `/finance/fees`, `/finance/invoices`, invoice issue/payments.
- `/analytics/overview`, attendance, assessments, questions, operations, usage, forecast, recordings, finance and report CSV exports.
- `/learning/outcomes`, competencies, paths/items, mappings, progress and mastery.
- `/credentials/templates`, credentials, public/authorized verification and revocation as specified by the contract.
- `/engagement/mentoring`, surveys/questions/responses, feedback.
- `/integrations/api-credentials`, webhooks/test/delivery history, storage, identity-providers/federation/synchronization, external notification providers and interoperability surfaces for OneRoster/QTI.
- `/messaging/conversations`, conversation messages/read state, message update/delete/delivery, blocks, reports/resolution, and tenant policy as declared in the existing OpenAPI contract.
- Platform operational/status/audit surfaces as defined in current OpenAPI.

For implementation: keep route paths, request names, enums, status codes, idempotency/concurrency semantics and auth scopes synchronized. No new route is complete until the schema, server validation, authorization tests, browser client and contract-parity gate agree.

---

# 24. Mandatory local workflow and commands

## 24.1 Sync before coding

Use `main` as the integration baseline and work on `main` only in the project’s requested workflow. Start with:

```bash
git status --short --branch
git fetch origin
git switch main
git pull --ff-only origin main
```

If the worktree is dirty, inspect and preserve user changes; do **not** use `reset --hard`, `clean -fd`, force-push, or history rewrite without explicit permission and an understood recovery plan. If a pull cannot fast-forward, stop and report the divergence rather than silently overwriting history. Review the actual current branch/head before making edits.

## 24.2 Bring up local infrastructure

From the repository root:

```bash
cp .env.example .env
# Edit .env for local-only credentials; never commit .env.
docker compose -f infra/compose.yaml up -d
```

Compose provides PostgreSQL on `5432`, Keycloak on host `8081` (container `8080`), LiveKit on `7880`/`7881` and UDP `7882`, optional Valkey on `6379`, and Egress with a shared `infra/var/recordings`-style mount. Verify that the Egress and server processing-volume paths agree before testing recordings.

For local-only boot, set `PLATFORM_DEV_MODE=true` as directed by `.env.example`/secret guard, or provide all real configured secrets. Never use dev secret defaults outside development. The web client’s Keycloak variables are documented in `.env.example`.

## 24.3 Backend

```bash
mvn -f apps/platform-server/pom.xml verify
```

This is the required backend gate; CI starts a fresh PostgreSQL 18 service, exercises the Flyway chain from zero via Maven integration tests, then starts the packaged JAR against a fresh smoke database and performs 30 concurrent health requests.

For local application development, set required environment variables then run:

```bash
mvn -f apps/platform-server/pom.xml spring-boot:run
```

Use the actual current `pom.xml`, `application.yaml`, Keycloak realm and `.env.example`; do not guess endpoints/secrets.

## 24.4 Web

```bash
npm --prefix apps/web install --ignore-scripts
npm --prefix apps/web test
npm --prefix apps/web run lint
npm --prefix apps/web run build
npm --prefix apps/web run dev
```

The current package’s `test` script runs the browser-demo store suite. Do not mistake those tests for comprehensive real-backend browser E2E, cross-browser, accessibility, or production auth qualification.

## 24.5 Repository, qualification, load and recovery

```bash
bash scripts/verify-repository.sh
python3 scripts/qualify-0.15.py
python3 scripts/load-smoke.py URL REQUESTS CONCURRENCY
python3 scripts/test-load-smoke.py
bash scripts/test-backup-restore-drill.sh
```

When k6 is installed, run `k6 run scripts/load-smoke.k6.js` against the intended test environment. Execute the destructive restore drill only against a uniquely named disposable target with `RESTORE_TARGET_IS_DISPOSABLE=true`. Do not run its `pg_restore --clean` against production.

## 24.6 Final evidence report

Before handing off, record:

- exact branch, HEAD SHA and clean/dirty status;
- changes made and rationale;
- unit/domain/integration/architecture/API/security tests run and actual command exit status;
- migration version uniqueness and fresh-database Flyway execution;
- web lint/tests/build;
- repository policy, qualification, SBOM/OSV status;
- latest CI run URL and per-job conclusion for the exact current SHA;
- remaining unimplemented requirements, external providers not qualified, and environment blockers;
- status/docs/ADR/OpenAPI changes made to reflect the decisions.

Do not claim production readiness based on static inspection or a green build alone.

---

# 25. Mandatory test coverage policy

Every domain module should have tests at the correct level:

- Unit/domain invariants and state machine boundaries.
- Property/boundary tests for calculations, offsets, recurrence, scoring and limits where suitable.
- PostgreSQL integration tests for constraints, migrations, locking, idempotency, transaction and tenant isolation.
- API contract tests for status codes, schema/validation and auth enforcement.
- Security tests for denied paths before data access, guessed IDs, cross-tenant IDs, inactive/revoked relationships, role combinations, branch scopes, stale versions and revoked sessions/tokens.
- Failure tests for provider timeout, interrupted work, retry exhaustion, transaction rollback, compensation failure, stale worker state, duplicate event and process-restart behavior.
- UI workflow tests and real browser E2E/accessibility as the release milestone requires.
- Load, fault injection, migration upgrade/rollback and backup/restore tests for release readiness.

Critical invariants to automate:

1. No cross-tenant data leakage.
2. No membership/role/session/branch relationship can cross tenant.
3. No role assignment can remove/deactivate the last active organization owner.
4. Learner cannot enumerate another learner’s enrollments, attempts, answers, grades, submissions, recordings or results.
5. Published assessments retain immutable question versions.
6. Server time controls attempts and submitted answer history never disappears.
7. Grade history is append-only/reproducible; review regrade creates new revision and audit record.
8. Assignment draft and final submission require published assignment and valid active-batch scope.
9. Guardian cross-learner access requires persisted, authorized relationship evidence.
10. Storage object is not AVAILABLE without valid size/hash/integrity metadata and authorized tenant reference.
11. Provider URLs/credentials never leak; webhook destination/secret/response logging obeys SSRF/secret policy.
12. Manual settlement cannot be duplicated or exceed invoice balance under concurrent requests.
13. Expired/suspended entitlement blocks disallowed new usage without destroying existing customer data.
14. Background work has bounded retries, idempotency, durable failures, tenant execution context and restart behavior.
15. All migrations are uniquely versioned and run from an empty PostgreSQL database.
16. All public contract changes pass contract-parity verification.

---

# 26. Local implementation plan: what to do next

This is the practical sequence for bringing the current checkout toward complete specification compliance without rewriting working modules.

## Gate A — Establish reproducible baseline

1. Sync `main` safely and capture HEAD SHA/status.
2. Run repository policy, backend verify, web tests/lint/build.
3. Review current GitHub Actions for that exact SHA.
4. Save the actual failure outputs; do not rely on old status docs or assumptions.
5. Create a module-by-module requirement matrix linking each requirement to canonical spec, class/API/migration, test and evidence.

## Gate B — Close correctness/security gaps before feature expansion

1. Complete the persisted Guardian-to-learner relationship and its lifecycle, audit, scope enforcement and tests. Until that exists, fail closed.
2. Finish end-to-end assignment/assessment/grade/review authorization scenarios across role combinations and branch/batch scopes.
3. Harden messaging conversation/thread UX and end-to-end delivery/read/moderation/abuse flows to the documented contract.
4. Finish recording worker crash/retry/recovery/archival tests, including initial scheduling race and deletion/migration compensation.
5. Finish live-class reconnect, stale-presence expiry, provider failure and large/poor-network observation coverage.
6. Close finance allocation/refund/reconciliation/provider qualification; do not market `MANUAL` as a payment gateway.
7. Audit all queries for tenant predicate, resource scope, pagination, indexes and absence of direct cross-module repository usage.

## Gate C — Complete specification-derived capabilities

1. Workflow engine/versioning/escalation.
2. Search and safe indexing/reindex.
3. Offline/kiosk/NFC/RFID/biometric attendance adapters and sync.
4. Longitudinal analytics/cohort comparisons/intervention workflows.
5. More complete federation/provider lifecycle and interoperability conformance.
6. Credential signing/portability/privacy.
7. Full tenant white-label settings/templates/custom-domain validation/operations.
8. PWA offline/push/sync.
9. Finish production deployment configuration and operator/user manuals.

## Gate D — Production candidate qualification

1. Real authenticated abuse/fuzz/authz bypass and privacy tests.
2. Upload/storage security and malicious payload/archive-bomb tests.
3. Session/token revocation/abuse, webhook replay/forgery/SSRF and federation/payment abuse tests.
4. Production-like representative load and capacity work, including exam-start/autosave/submit bursts and live media joins; report p50/p95/p99.
5. WCAG 2.2 AA audit and remediation.
6. PostgreSQL PITR/full restore, object-store recovery, measured RPO/RTO.
7. Failure injection/provider degradation/failover and worker restart exercises.
8. Migration upgrade/rollback/rehearsal on representative populated data.
9. Operational runbooks, alerting/status page, incident process, privacy tooling, release/update automation and documented SLA.
10. Require all checks green for exact release commit; then bump version via `scripts/set-version.py`, create release commit and tag only after clean full verification.

---

# 27. Copyable instruction for a local coding agent

Use this prompt with a repository-aware local coding agent. It is deliberately strict about evidence and current source.

> **Role:** Act as UEP’s senior platform engineer, domain architect, security engineer, and qualification lead. The product requirements are in `docs/MASTER_IMPLEMENTATION_PLAN.md` (102 sections); accepted decisions are in `docs/adr/`; domain invariants are in `docs/architecture/`; public API is `docs/api/openapi.yaml`; qualification gates are `docs/QUALIFICATION_0.15.md`, `scripts/verify-repository.sh`, and `scripts/qualify-0.15.py`.
>
> **Start by syncing and inspecting current source:** inspect `git status --short --branch`, fetch/pull `origin/main` with fast-forward only, capture current SHA, inspect the live docs, code, migrations, tests, CI and unmerged pull requests. Preserve existing user work. Do not hard reset, clean untracked files, force-push, rewrite history or create branches unless explicitly approved. Work on `main` according to the project workflow.
>
> **Objective:** implement every accepted requirement applicable to the current milestone and close all specification-derived gaps in measurable stages. Do not blindly rewrite the platform or introduce unrelated frameworks. Follow modular-monolith boundaries; module-owned repositories; tenant-aware composite constraints; trusted OIDC tenant context; RBAC/context/entitlement checks; stable OpenAPI; immutable academic versions; audit history; idempotency; bounded background work; provider adapters; pagination; stream processing; accessibility; operational qualification.
>
> **Execution:** build a requirements-to-evidence matrix before changes. For each gap, identify the canonical requirement, current code/migration/API, why it is incomplete, a minimal root-cause fix, exact tests, docs/contracts that must change, and dependencies. Implement in dependency order and small, reviewable increments. Every security-sensitive fix must have positive and negative regression tests. Keep database changes as unique forward-only Flyway migrations; never renumber or edit an applied migration. Ensure tenant and resource scopes apply in list/detail/mutate/stream paths. Do not claim a UI/demo stub is production behavior.
>
> **Required validation:** `mvn -f apps/platform-server/pom.xml verify`; web `npm test`, `npm run lint`, `npm run build`; `bash scripts/verify-repository.sh`; `python3 scripts/qualify-0.15.py`; load-smoke safety tests; full fresh-Postgres Flyway/application boot; all current CI jobs green on the exact final SHA. Run real provider/load/restore/accessibility tests where applicable or explicitly report them as not yet executed.
>
> **Handoff:** report verified facts, files changed, migration version, contracts changed, tests with pass/fail output, exact latest CI run URL and job conclusions, remaining gaps/blockers, and the next acceptance gate. Update implementation status/architecture/ADR/OpenAPI/docs after decisions. Never state “complete” until all applicable acceptance criteria and gates have evidence.

---

# 28. Canonical source index for local work

| Source | What to consult it for |
|---|---|
| `README.md` | Stack, quick overview/build commands (historical status text may lag current CI). |
| `docs/MASTER_IMPLEMENTATION_PLAN.md` | Whole product scope, 102 sections, all milestone definitions. |
| `docs/IMPLEMENTATION_STATUS.md` | Current implementation inventory and explicit gap list; reconcile its CI SHA with live Actions. |
| `docs/QUALIFICATION_0.15.md` | Current reliability/security/restore qualification gates. |
| `docs/VERSIONING.md`, `CONTRIBUTING.md` | Version discipline, DCO sign-off, CI and change requirements. |
| `docs/architecture/TENANCY_AND_SECURITY.md` | Tenant context and isolation invariants. |
| `docs/architecture/IDENTITY_AND_ORGANIZATION.md` | OIDC, membership, RBAC, privileged roles, sessions and bootstrap. |
| `docs/architecture/ACADEMIC_CORE.md` | Flexible hierarchy, enrollments, teaching assignments and capacity. |
| `docs/architecture/SCHEDULING_AND_COMMUNICATION.md` | Recurrence/materialization, conflict locking, announcement snapshots, outbox and Today. |
| `docs/architecture/LEARNING_CONTENT.md` | Upload/version/provider/retention/security contracts. |
| `docs/architecture/ASSESSMENT_CORE.md` | Immutable questions/assessments, timing, attempt and autosave rules. |
| `docs/architecture/GRADING_AND_ACADEMIC_REVIEW.md` | Immutable grading, review workflow, regrading and scope. |
| `docs/architecture/ASSIGNMENTS_AND_GRADEBOOK.md` | Submission lifecycle, weights and gradebook boundary. |
| `docs/architecture/LIVE_LEARNING.md` | LiveKit control/media separation and role permissions. |
| `docs/architecture/RECORDING_PLATFORM.md` | Capture, processing, storage tiers, integrity, playback, retention. |
| `docs/architecture/FINANCE_AND_COMMERCIALIZATION.md` | Institution fees, manual settlement boundary, commercial subscription/quota/white-label. |
| `docs/architecture/ANALYTICS_AND_OPERATIONS.md` | Derived analytics, bounds, export and forecast contract. |
| `docs/architecture/ADVANCED_ACADEMIC_PLATFORM.md` | Outcomes, competencies, credentials and engagement. |
| `docs/architecture/INTEGRATION_PLATFORM.md` | API credentials, webhook/SSRF, federation, OneRoster/QTI. |
| `docs/architecture/NATIVE_CLIENT_SCOPE.md` | Multi-client sequence and server authority. |
| `docs/adr/0001`–`0013` | Accepted architecture decisions and consequences. |
| `docs/api/openapi.yaml` | Exact route/schema/security/API contract (174 paths in this snapshot). |
| `docs/commercial/LICENSING_ARCHITECTURE.md` | Entitlement decision model. |
| `docs/commercial/OFFLINE_LICENSE_FORMAT.md` | Future signed offline-license envelope/anti-rollback behavior. |
| `docs/operations/IDENTITY_BOOTSTRAP.md` | Reference Keycloak realm, first owner, session incident response. |
| `.github/workflows/ci.yml` | Actual automated gate commands. |
| `scripts/verify-repository.sh` | Version, source, secret, contract-parity and qualification entrypoint. |
| `scripts/qualify-0.15.py` | Large static qualification contract matrix (current file is substantial; read before changing). |
| `scripts/backup-restore-drill.sh` + test | Safe disposable backup/restore procedure. |
| `scripts/load-smoke.py`, `scripts/load-smoke.k6.js` + tests | Latency/percentile smoke and load profiles. |

---

## Final rule

UEP is the platform; the web application is the first client. Preserve the direction:

`Clients → Stable Contracts/APIs → Domain Platform → Data / Storage / Media → Infrastructure Adapters`.

Never put Google Drive rules inside examinations, LiveKit logic inside course models, React behavior inside academic rules, provider credentials in the browser, or commercial plan-name logic inside domain authorization. Implement the complete requirements incrementally, with root-cause fixes, regression evidence, and truthful qualification status.
