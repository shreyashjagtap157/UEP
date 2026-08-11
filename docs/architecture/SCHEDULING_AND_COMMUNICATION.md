# Scheduling and Communication Architecture

## Scope

Milestone `0.4.0.0` establishes the platform calendar, recurring schedule engine, class-session anchors, conflict controls, announcements, durable notification pipeline, optional SMTP delivery, notification preferences, and universal Today dashboard.

The design intentionally keeps scheduling authoritative in the control plane. Live media, attendance, recordings, and future assessment execution attach to stable schedule/class-session identifiers rather than owning calendar state themselves.

## Scheduling model

Scheduling uses two persistent levels:

- `schedule_series` stores the user's scheduling intent and recurrence rule;
- `schedule_occurrence` stores every materialized occurrence as an immutable original time plus mutable exception state.

Recurring series are finite. A request must specify either an occurrence count or an end-local-time, and a series can materialize at most 1,000 occurrences. This prevents unbounded rows/jobs from a malformed recurrence rule.

Supported recurrence frequencies are one-time, daily, weekly, and monthly. Weekly schedules use explicit weekdays. Monthly schedules use a day-of-month rule and clamp to the last valid day in shorter months.

Every series carries an IANA timezone. Local recurrence times are converted using zone rules. Nonexistent DST-gap times and ambiguous DST-overlap times are rejected instead of silently shifting to a different wall-clock time.

## Materialized exceptions and history

Rescheduling one occurrence does not rewrite the series or historical recurrence rule. The occurrence retains `original_starts_at` and records its new start/end, substitute teacher, room override, exception reason, and optimistic version.

Cancellation similarly changes occurrence/series lifecycle state rather than deleting historical schedule rows.

`class_session` is created for each class occurrence. Later live-class, presence/attendance, recording, and classroom-resource modules should reference this stable anchor rather than a provider room identifier.

## Concurrency and conflicts

Schedule writes acquire a tenant-scoped PostgreSQL advisory transaction lock before conflict evaluation and persistence. This closes the race where two concurrent requests both preflight an empty room/teacher window and then commit overlapping schedules.

Conflict detection currently covers:

- teacher overlap;
- batch/cohort overlap;
- physical room overlap within a branch;
- exam overlap;
- institutional holiday overlap.

Conflict overrides require `SCHEDULE_CONFLICT_OVERRIDE`, a non-empty reason, and a first-class `schedule_conflict_override` record containing the actor and conflict evidence. A generic audit event is also emitted.

Calendar authorization is scope-aware. Tenant administrators can manage the tenant calendar; branch administrators can manage only branches explicitly assigned through RBAC; teachers and learners consume self-scoped calendars derived from teaching assignments/enrollments.

## Announcements

Announcements separate authoring state from audience resolution. Supported target types are organization, branch, course, batch, subject, role, and membership.

Drafts and scheduled announcements retain target rules. The actual recipient snapshot is resolved at publication time, not creation time. This means a learner who joins a batch after an announcement is scheduled but before it is published receives the announcement.

Published recipient membership IDs are stored in `announcement_recipient`, providing stable delivery evidence and preventing later enrollment changes from rewriting the historical publication audience.

Branch-scoped announcement administrators are authorized against both the announcement's existing administrative branch and its proposed target scope. A guessed UUID therefore cannot be used to retarget another branch's draft.

Announcement attachments remain an explicit integration point for milestone 0.5 because uploaded objects, MIME validation, storage providers, malware scanning, and resource-version lifecycle belong to the Learning Content/storage subsystem rather than being duplicated here.

## Durable notifications

Business modules do not send email or create inbox rows synchronously. They write a `notification_outbox` event and its recipient snapshot in the same business transaction.

A scheduled processor materializes per-recipient notifications and delivery records. Processing is idempotent through tenant-scoped outbox deduplication and per-delivery idempotency keys.

Outbox failures use bounded exponential retry. After 12 processing failures an outbox record becomes dead-lettered instead of retrying forever. Email delivery uses a separate bounded retry worker. Deployments with email disabled explicitly record email delivery as `SKIPPED`; in-app delivery remains operational.

Tenant ownership is enforced through composite tenant/outbox and tenant/notification foreign keys. Operational backlog counters are tenant-scoped and require `NOTIFICATION_OPERATIONS_VIEW`.

## Notification preferences

Each membership can independently enable or disable in-app and email delivery per notification event type. Defaults are enabled unless an explicit preference exists. Preferences carry optimistic versions to prevent silent concurrent overwrites.

The event vocabulary already reserves later domain events such as resource release, assignment due, result publication, recording readiness, payment due, and academic-review changes. Later modules should publish through `NotificationPublisher` rather than directly depending on email infrastructure.

## Today dashboard

`/api/v1/today` calculates the current local date from organization timezone settings and returns the current user's authorized schedule plus unread notification count.

For users with schedule-management scope it also reports administrative daily counts such as classes, exams, and distinct scheduled learners. Personal users receive only their own schedule-derived view.

The browser composes Today as the primary application surface, but date range, tenant scope, participant scope, and notification state remain server authoritative.

## Degradation model

- SMTP unavailable: outbox/business transaction succeeds; email retries independently.
- Email disabled: in-app notification remains available and email is recorded as skipped.
- Announcement publisher restarts: scheduled announcements remain persisted and are retried by the poller.
- Notification materializer repeatedly fails: the event becomes dead-lettered and visible to notification operations.
- Media services unavailable: schedule/class-session state remains valid because scheduling is not coupled to LiveKit/Jitsi.

This keeps communication infrastructure from becoming a prerequisite for critical academic state changes.
