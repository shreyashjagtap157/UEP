# ADR 0005: Materialized Scheduling and Durable Notification Outbox

- Status: Accepted
- Milestone: 0.4.0.0

## Context

The platform must support recurring academic schedules, exception handling, conflict detection, multiple time zones, branch-scoped administration, reliable announcements, and inexpensive deployments where email may be absent or temporarily unavailable.

Recomputing recurrence indefinitely at read time makes historical exceptions and future cross-domain references unstable. Sending notifications inside business requests makes academic operations depend on external mail infrastructure and creates lost-notification failure windows.

## Decision

Persist schedule intent as a finite `schedule_series` and materialize every occurrence into `schedule_occurrence`. Preserve the original occurrence time and apply rescheduling/cancellation as versioned exception state. Create a stable `class_session` row for class occurrences.

Serialize conflict-sensitive schedule writes per tenant with a PostgreSQL advisory transaction lock. Require explicit permission and persistent evidence for conflict overrides.

Persist announcements separately from their resolved recipients. Resolve scheduled-announcement audiences at publication time, then snapshot recipients.

Use a transactional notification outbox. Business modules enqueue tenant-owned events and recipient snapshots; background processors materialize in-app notifications and optional email deliveries with idempotency, bounded retry, and dead-letter handling.

## Consequences

Calendar reads become simple and deterministic, historical exceptions remain reproducible, and later attendance/media/recording modules receive stable class-session identifiers. The storage cost is bounded by finite occurrence materialization.

Critical business transactions no longer wait for SMTP. Notification failures are observable and retryable. The tradeoff is additional durable tables/workers and the requirement to operate outbox/dead-letter monitoring in production.
