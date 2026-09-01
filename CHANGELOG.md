# Changelog

## 0.6.0.0 — Assessment Core

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
