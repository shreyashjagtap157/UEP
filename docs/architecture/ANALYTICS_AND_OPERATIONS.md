# Analytics and Operations

The 0.12 analytics subsystem is deliberately derived from authoritative transactional records. It does not maintain a competing academic, finance, recording, or licensing source of truth.

## Scope

The tenant-scoped API provides:

- attendance distribution and attendance rate;
- assessment attempts, submissions, published grades, pass counts, and average score;
- question-quality metrics from published grade items;
- recording count, duration, storage bytes, and processing outcomes;
- finance totals where the caller is authorized for finance visibility;
- entitlement usage and current hard limits;
- operational audit, notification, dead-letter, and active-session counters;
- 30-day recording/storage growth forecasts;
- bounded CSV exports for attendance, assessments, recordings, and finance.

## Boundaries

Analytics requests require explicit tenant context and permission checks. Windows are bounded to 366 days. Financial figures are omitted from the general overview unless `FINANCE_VIEW` is also present. Operations counters use `OPERATIONS_VIEW` rather than the general analytics permission.

Analytics queries are read-only and execute against tenant-filtered tables. Migration V020 adds indexes aligned with the time-window queries used by this module.

## Forecasting

The initial storage forecast is intentionally transparent: it uses authoritative recording bytes and duration observed during the preceding 30 days and projects a linear rate. This is a capacity-planning signal, not a billing commitment or a probabilistic prediction.

## Export contract

Exports are synchronous, permission-protected CSV snapshots. The supported report identifiers are `attendance`, `assessments`, `recordings`, and `finance`. Large scheduled exports and object-storage delivery remain future work under the integration/reliability milestones.
