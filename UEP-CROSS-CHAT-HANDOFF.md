# Universal Education Platform (UEP) — Cross-Chat Handoff

## 1. Purpose

This document is the authoritative continuation handoff for the UEP project as of 2026-09-08.

The goal is to continue the project in another chat without guessing repository state, silently accepting stale conversational claims, or reconstructing code that is not present in Git.

## 2. Verified Git source included in this transfer

The source repository that is actually persisted and recoverable in this environment is the original verified UEP Git bundle.

- Repository: Universal Education Platform (UEP)
- Branch: `main`
- Verified HEAD before this handoff commit: `e806e4d104146828b0e5b6e9e322c9e4801e3b4a`
- Short HEAD: `e806e4d`
- Original captured commits: 32
- Original tracked files: 565
- Original migration files: 24
- Original refs:
  - `refs/heads/main` → `e806e4d104146828b0e5b6e9e322c9e4801e3b4a`
  - `refs/remotes/origin/main` → `fcd378a95cd2f9b6a33e9630b135e4c62b76d2d8`
  - `HEAD` → `e806e4d104146828b0e5b6e9e322c9e4801e3b4a`

The original bundle was independently verified with `git bundle verify` from a repository context and Git reported that the bundle records a complete SHA-1 history.

## 3. Important recovery limitation

Later chat turns contained many implementation claims and commit SHAs after `e806e4d`. Those changes were made in a session-local worktree that is no longer persisted or accessible in the current execution environment.

The accessible storage contains the original `e806e4d` bundle, but not the later source tree or its later Git objects. Therefore:

- the later changes are NOT represented as Git history in this transfer;
- their earlier conversational descriptions must NOT be treated as source truth;
- do not claim those later changes exist in the repository until a future chat verifies them in an actual checkout;
- the durable Git source in this transfer is the verified `e806e4d` history plus this handoff commit.

This is intentional: do not fabricate repository history.

## 4. Existing architecture and engineering rules

Preserve the integrated architecture:

`CLIENTS → CONTRACTS/APIs → DOMAIN PLATFORM → DATA/STORAGE/MEDIA → INFRASTRUCTURE ADAPTERS`

The server remains authoritative for:

- authorization and tenant isolation;
- exams, attempts, timing, submissions;
- grades/results and academic history;
- attendance;
- resource permissions;
- entitlements/licensing;
- payment state/reconciliation;
- recording access;
- credentials;
- audit history;
- privacy operations.

Offline clients may buffer explicitly permitted transport events but must never acquire local authority over authoritative academic, financial, credential, licensing, attendance, or access-control state.

For every substantive change:

1. inspect compatibility across the affected codebase;
2. inspect module boundaries and integrations;
3. implement actual production behavior, not placeholders;
4. add appropriate tests/invariants;
5. run every qualification gate actually available;
6. fix defects, regressions, warnings and contract drift;
7. remove obsolete/duplicate implementation;
8. integrate into the existing system;
9. update authoritative documentation and cumulative changelog;
10. commit coherent completed work;
11. never claim unavailable runtime gates passed;
12. never leave production-facing fake-success/TODO placeholders.

## 5. Specification completion contract

Production Web GA / 1.0 requires, at minimum:

- web application complete;
- no critical known defects;
- all P0/P1 functionality integrated;
- security gates passing;
- real performance evidence;
- successful backup restore;
- tenant isolation verified;
- assessment history reproducible;
- recording/storage failover verified;
- payments reconciled;
- licensing enforced;
- WCAG 2.2 AA target reached;
- documentation complete.

## 6. Major functionality already present in the verified baseline

The verified baseline contains substantial implementations for:

- multi-tenancy and tenant-safe persistence;
- identity, memberships, RBAC, branches and organization settings;
- revocable sessions and strong-auth foundations;
- academic periods, programs, curriculum, cohorts, enrollment and teaching assignments;
- scheduling and class sessions;
- announcements, notifications/outbox and messaging foundations;
- learning resources, versioning, resumable uploads, storage adapters and retention policies;
- question bank and immutable question versions;
- assessment construction, availability, assignment, authoritative timing, autosave and submission;
- objective/manual grading, rubrics, immutable revisions, review/challenge/regrading foundations;
- assignments, submissions, gradebook and learner progress;
- LiveKit live classrooms, presence, attendance policy and moderation foundations;
- recording orchestration/storage lifecycle/playback authorization;
- invoices, installments, payments, receipts, SaaS plans/entitlements/quotas/usage;
- analytics and operations/reporting foundations;
- learning outcomes, competencies, learning paths, credentials/public verification, mentoring and surveys;
- API credentials, signed webhooks, OIDC federation, OneRoster and QTI foundations;
- external storage/notification adapters;
- reliability/qualification tooling.

The exact baseline state must always be read from the checked-out source rather than inferred from this summary.

## 7. High-priority known incomplete areas from the complete specification

These are specification-derived areas that the baseline itself identifies as incomplete/foundational and that must be verified against the actual source during continuation:

### Communication

Complete persistent messaging including conversations/threads, read/unread, delivery state, moderation/reporting/blocking, retention, audit, abuse controls, tenant/permission isolation and notification integration.

### Advanced grading

Best-of-N, dropped-lowest policies, extra credit, minimum component requirements, pass/fail conditions, rounding/weighting policies, anonymous grading, double grading, moderation, final adjudication and fully reproducible grade calculation/history.

### Attendance

Kiosk operation, NFC, RFID, biometric integration boundary, imports/API, offline synchronization, replay/idempotency, conflict resolution and full auditability.

### Workflow

Configurable workflows, states, transitions, approvals, roles, deadlines, escalation, automatic actions, conditional routing, notifications, audit, retries/idempotency, versioning and historical reproducibility, integrated with academic review, finance, credentials, privacy and administration.

### Search

Tenant-safe permission-aware global search across people, courses, batches, content, assignments, assessments, recordings, credentials and administrative entities, with indexing/reindexing, rate limiting and scalable architecture.

### Finance

Discounts, scholarships, credit/adjustment notes, payment allocation, settlement/reconciliation edge cases, real provider adapters, financial audit workflow, accounting exports/reporting, robust idempotency and immutable financial history.

### Federation

Multiple providers, provider lifecycle, claim/group mapping, deprovisioning, session handling, enterprise administrative workflow and federation security testing.

### Interoperability

LTI, CASE, xAPI, Caliper, SCORM, mature OneRoster/QTI support, Open Badges and CLR-compatible exchange must be treated as actual interoperability requirements; foundation-only implementations do not equal conformance.

### Credential portability

Signing, issuer identity, portable payloads, verification, status/revocation, Open Badges, CLR-style exchange and privacy-preserving verification.

### White-labeling

Tenant branding, login branding, email/certificate/invoice templates, support information, tenant-specific communications and isolation.

### Live classroom

Server-side moderation enforcement, stale-heartbeat expiry, scheduling lifecycle, reconnect/recovery, browser media errors, large-class testing, poor-network behavior, media-plane failure handling and operational observability.

### Recording platform

Crash-safe/idempotent workers, retries/recovery, transcoding ladder, quality management, range-aware playback, CDN/distribution, worker scaling, archive migration controls, storage recovery and processing observability.

### Analytics

Longitudinal learner analytics, cohort comparison, competency progression, outcome mastery, teacher effectiveness, engagement funnels, intervention/remediation, richer question diagnostics, scheduled reports, configurable dashboards and safe aggregation at scale.

### PWA/offline

Installability, application shell, service worker, caching strategy, push, limited offline metadata, safe offline workflows and explicit offline exam/attendance synchronization without unsafe grade authority.

## 8. Production qualification still required

Even when functionality exists, real qualification remains mandatory for:

- authenticated API abuse/fuzz testing;
- authorization bypass testing;
- upload/storage security;
- token/session abuse;
- webhook replay/forgery;
- federation attacks;
- payment abuse/replay;
- dependency/security scanning;
- privacy authorization testing;
- external penetration/legal review;
- p50/p95/p99 latency and throughput;
- database/query/exam autosave/join/recording/worker/large-tenant performance;
- browser/keyboard/screen-reader/zoom/reflow/contrast/reduced-motion WCAG testing;
- PostgreSQL PITR and full restore;
- object-storage/configuration/key recovery;
- measured RPO/RTO and failure injection;
- provider degradation/failover;
- migration/upgrade/interruption/rollback testing.

The verified baseline explicitly warns that repository-local qualification is not equivalent to Production Web GA qualification.

## 9. Native client scope — explicitly part of the overall project

UEP is not web-only.

The overall product includes native clients for:

- Windows;
- macOS;
- Linux;
- Android;
- iOS/iPadOS;
- institutional kiosk clients where required.

The existing plan sequences production native applications after Web GA so shared contracts can stabilize first. That sequencing is retained, but native clients are a first-class product scope and must be tracked in the project completion plan rather than omitted from the project definition.

The native-client readiness contracts include:

- OpenAPI;
- WebSocket protocol;
- media-session protocol;
- file-transfer protocol;
- notification contract;
- auth/OIDC flows;
- offline-sync versioning;
- license-entitlement protocol;
- client-version policy.

The verified baseline contains planning/readiness material but no production native application trees. A future continuation must therefore verify the actual native source separately and integrate it through the same authoritative platform contracts.

## 10. Meeting model

The authoritative meeting model is based on scheduled `ClassSession` records:

1. schedule creates a `ClassSession`;
2. authorized teacher/admin opens the live classroom;
3. UEP verifies tenant/membership/permission;
4. UEP issues a short-lived media token;
5. browser/native client connects to LiveKit or another selected provider;
6. UEP remains control plane and the provider remains media plane;
7. media, presence and classroom interaction use the live substrate;
8. UEP records presence/attendance evidence;
9. teacher/admin ends the class;
10. recording may continue asynchronously;
11. recordings are processed/stored/authorized through UEP.

The same live substrate should support later meeting types such as office hours, mentoring, parent meetings, support sessions, counseling and administrative meetings without rebuilding the media subsystem.

## 11. Continuation procedure

1. Materialize this bundle.
2. Run `git bundle verify` from repository context.
3. Clone into a fresh worktree.
4. Check out `main`.
5. Verify `git rev-parse HEAD`, `git status --short --branch`, and `git fsck --full`.
6. Read `CHANGELOG.md`, `docs/IMPLEMENTATION_STATUS.md`, `docs/MASTER_IMPLEMENTATION_PLAN.md`, and this handoff.
7. Inspect the actual source before relying on any earlier conversational claim.
8. Continue implementation in the existing architecture.
9. Maintain cumulative changelog history.
10. At completion, produce a repository-wide audit distinguishing fully implemented, partial, missing, insufficiently tested, unqualified, external, environment-blocked, and exact release-blocker states.

## 12. No false completion

Do not declare UEP complete merely because named modules exist.

The final audit must explicitly answer:

- what is fully implemented;
- what is partial;
- what is missing;
- what is insufficiently tested;
- what is not production-qualified;
- what remains external;
- whether production stubs/TODOs/placeholders remain;
- whether APIs/UI/jobs/adapters/observability/deployment are integrated;
- whether tenant isolation is safe;
- whether academic records are reproducible;
- whether finance is reconcilable;
- whether recordings/media are failure-safe;
- whether privacy controls are safe;
- whether migrations/upgrades are valid;
- whether accessibility/performance are genuinely demonstrated.

## 13. Current objective

Complete the entire UEP project to the highest practical production standard, including its web and native client tracks, without fabricating evidence or repository history.
