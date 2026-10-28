# Implementation Status

## Current verified state — 2026-10-28

`0.15.0.0-SNAPSHOT` remains the development version. The latest dependency merges are PR #25 (`@types/node` 26.6.5) and PR #22 (`livekit-client` 2.23.0); PR #22 merged as `8290fc16f9d18d3f3350f22934be5b1ee33a8ccc`. The last independently confirmed full CI run is GitHub Actions run `40686109376` / run #460, which completed successfully across web, backend, repository-policy/qualification, and vulnerability-scan jobs after the recording-access changes and regression tests were added. Subsequent assessment catalog, Guardian permission, question-bank authorization, and qualification-script changes are committed on `main` but are **not yet represented by a confirmed green CI result in this status record**.

### Verified CI evidence

- Backend Maven verification passed, including application-context startup and packaged-server smoke coverage in the CI backend job.
- Web dependency installation, lint, demo workflow tests, and production build passed.
- Repository-policy qualification passed, including backup/restore safety guards, load-smoke percentile/latency checks, and the 0.15 qualification harness.
- Java dependency SBOM generation and OSV dependency scanning passed.
- Dependabot updates to `@types/node` 26.6.5 (PR #25) and `livekit-client` 2.23.0 (PR #22) were merged only after their refreshed CI checks passed.

Run #460 validated the PR merge candidate against the then-current `main` before the later assessment and question-bank changes described below. Those newest changes still require a fresh successful full CI run. This is repository CI evidence, not a claim that production deployment or local execution was performed here. This is strong repository-local qualification evidence, but it is **not** by itself Production Web GA evidence; production-scale capacity, external/provider qualification, accessibility, recovery, operational, interoperability, and native-client qualification remain separate requirements.

## Scope correction: no swarm-agent functionality

UEP is the Universal Education Platform and does **not** contain swarm-agent functionality. The previously introduced unrelated swarm-agent implementation and tests have been removed from `main`. Repository searches for `Swarm` and related swarm implementation markers return no matches. The remaining occurrences of `agent` are unrelated build/tool terminology and are not a UEP product feature.

Swarm-agent orchestration, specialist agents, swarm providers, agent playgrounds, and related APIs/configuration belong to other projects and must not be reintroduced into UEP.

## Current 0.15 Reliability and Performance Qualification

Implemented/qualified repository-local foundations include:

- repeatable load probes and k6 profile with explicit percentile targets;
- database reliability/performance indexes;
- bounded connection-pool configuration and graceful shutdown;
- backup/restore drill tooling and safety guards;
- tenant-isolation and identity/security configuration checks;
- repository verification and qualification harness;
- CI execution of the above gates.

Production qualification still requires production-like load/capacity evidence, provider qualification, operational runbooks, failure injection, and recovery evidence where the relevant environment/provider is required.

## Recent security and reliability fixes — 2026-10-28

- The finance `MANUAL` payment provider is no longer `final`, allowing Spring Modulith's class-based observability proxy to be generated. This fixed the application-context failure observed in the earlier backend CI run; refreshed backend CI passed afterward.
- Browser-demo course metrics now use the same published-catalog scope as role-specific course views. Mentor summaries resolve an active learner through the authorized mentor relationship and do not include draft-course progress.
- Local-demo state writes now report storage failures, avoid broadcasting a change event for a failed write, retain the attempted state in the current tab, and retry persistence when storage recovers. The UI warns when a change is visible only in the current tab.
- Recording detail, playback-token issuance, and stream opening now share a batch-scope authorization check. Members without `RECORDINGS_MANAGE` can access recordings only for their active batches; stream opening rechecks enrollment so a token issued before revocation is not sufficient afterward. Regression coverage was added for denied cross-batch access, permitted in-batch access, and revocation.
- The assessment catalog now returns published assessments assigned to the current member's active batches; version listing applies the same scope. Student permissions explicitly include `ASSESSMENTS_TAKE`, while Guardian permissions are separate and read-only for learner academic workflows.
- Flyway migration `V039__student_assessment_take_guardian_read_only.sql` applies the Student/Guardian permission correction to existing tenants; the 0.15 qualification script checks its required grants/removals.
- Question-bank catalog and version reads now require `ASSESSMENTS_MANAGE`; non-manager UI sessions do not fetch or render the question-bank catalog. Regression tests cover denied reads before repository access.
- `scripts/qualify-0.15.py` now requires regression coverage for recording batch scope, assessment visibility, Student/Guardian permissions, and question-bank authorization.

## Current gradebook capability

The gradebook calculation path now supports a deterministic policy model for:

- drop-lowest;
- best-of-N;
- extra-credit percentage points;
- minimum completed components;
- weight renormalization after exclusions;
- configurable rounding;
- validation of scores, maximum points, and weight ranges;
- regression tests for policy behavior and invalid configurations.

The default policy preserves the existing standard calculation behavior.

## Major implemented platform areas

The verified repository contains substantial implementations across:

- multi-tenancy, tenant context, memberships, RBAC, branches and organization settings;
- sessions, identity/security foundations and authorization boundaries;
- academic periods, programs, courses/subjects/modules, cohorts, enrollment and teaching assignments;
- scheduling, recurring series, conflict detection, class sessions and calendars;
- announcements, notification outbox, in-app notifications, email integration and dead-letter handling;
- learning resources, immutable versions, resumable uploads and provider-independent storage adapters;
- question banks, immutable question versions, assessments, timing, autosave, submission and grading foundations;
- assignments, submissions, gradebook and learner progress;
- LiveKit live classrooms, presence, attendance and moderation foundations;
- recording orchestration, storage lifecycle, integrity, retention and playback authorization;
- invoices, installments, payments, receipts, SaaS plans, entitlements, quotas and usage;
- analytics, operations and reporting foundations;
- learning outcomes, competencies, learning paths, credentials, mentoring and surveys;
- API credentials, signed webhooks, OIDC federation and OneRoster/QTI foundations;
- reliability, security and qualification tooling;
- browser-only synthetic demonstration workspaces for the documented personas.

The browser demonstration is explicitly synthetic and is not a production authentication/security boundary. Production authorization remains server-side.

## Specification-derived areas requiring continued completion/audit

The project completion contract remains broader than the currently green CI suite. Continue implementation and qualification for:

1. **Communication:** persistent conversations/threads, delivery/read state, moderation, reporting/blocking, retention and abuse controls.
2. **Advanced academic review:** pass/fail rules, anonymous/double grading, moderation, final adjudication, immutable reproducibility and review-driven regrading.
3. **Attendance integrations:** kiosk/NFC/RFID/biometric boundaries, imports/API, offline synchronization, replay/idempotency and conflict resolution.
4. **Workflow engine:** configurable states/transitions, approvals, deadlines, escalation, conditional actions, retries/idempotency, versioning and historical reproducibility.
5. **Search:** permission-aware tenant-safe global search, indexing/reindexing, rate limiting and scale.
6. **Finance:** discounts, scholarships, credits/adjustments, payment allocation, settlement/reconciliation edge cases, accounting exports and real provider qualification.
7. **Federation:** multiple providers, lifecycle, claim/group mapping, deprovisioning and security qualification.
8. **Interoperability:** LTI, CASE, xAPI, Caliper, SCORM, mature OneRoster/QTI, Open Badges and CLR-compatible exchange.
9. **Credential portability:** signing, issuer identity, status/revocation and privacy-preserving verification.
10. **White-labeling:** tenant branding, templates and tenant-specific communications/support settings.
11. **Live classroom:** reconnect/recovery, stale-heartbeat expiry, browser/media errors, large-class and poor-network qualification, provider failure handling and observability.
12. **Recording platform:** crash-safe workers, retries/recovery, transcoding ladder, CDN/distribution, archive migration and storage recovery.
13. **Analytics:** longitudinal learner analytics, cohort comparison, competency progression, intervention/remediation and safe aggregation at scale.
14. **PWA/offline:** installability, service worker, caching, push and safe offline synchronization.
15. **Native clients:** Windows, macOS, Linux, Android, iOS/iPadOS and institutional kiosk clients, integrated through stabilized platform contracts.

## Production qualification still required

Before declaring Production Web GA / 1.0, the repository must have real evidence for authenticated abuse/fuzz testing, authorization bypass testing, upload/storage security, session/token abuse, webhook replay/forgery, federation and payment abuse, privacy authorization, external penetration/legal review, production-like p50/p95/p99 performance, accessibility/WCAG 2.2 AA testing, PostgreSQL PITR/full restore, object-storage recovery, measured RPO/RTO, failure injection, provider degradation/failover, migration/upgrade/rollback testing, and operational readiness.

## Version

`VERSION` remains `0.15.0.0-SNAPSHOT`. No stable release is declared by this document.
