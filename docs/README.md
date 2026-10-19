# Document Index

All product documentation, in reading order.

## Start here
- `../README.md` — repository overview, current milestone, build instructions.
- `MASTER_IMPLEMENTATION_PLAN.md` — the full product/implementation specification (102 sections).
- `IMPLEMENTATION_STATUS.md` — what is implemented per milestone.
- `VERSIONING.md` — release/tag discipline (`stable.major.minor.patch`).
- `QUALIFICATION_0.15.md` — the 0.15 reliability/performance qualification gates.

## Architecture domain notes (`architecture/`)
Tenancy and security → `TENANCY_AND_SECURITY.md` · Identity/organization → `IDENTITY_AND_ORGANIZATION.md` · Academic core → `ACADEMIC_CORE.md` · Scheduling/communication → `SCHEDULING_AND_COMMUNICATION.md` · Assessment → `ASSESSMENT_CORE.md` · Grading/review → `GRADING_AND_ACADEMIC_REVIEW.md` · Assignments/gradebook → `ASSIGNMENTS_AND_GRADEBOOK.md` · Learning content → `LEARNING_CONTENT.md` · Live learning → `LIVE_LEARNING.md` · Recording → `RECORDING_PLATFORM.md` · Analytics/operations → `ANALYTICS_AND_OPERATIONS.md` · Advanced academic → `ADVANCED_ACADEMIC_PLATFORM.md` · Integration → `INTEGRATION_PLATFORM.md` · Finance → `FINANCE_AND_COMMERCIALIZATION.md` · Client strategy → `NATIVE_CLIENT_SCOPE.md` · Foundation status → `FOUNDATION_STATUS.md`.

## Decision records (`adr/`)
0001 modular monolith · 0002 commercial entitlements · 0003 identity/RBAC/session boundaries · 0004 flexible academic core · 0005 materialized scheduling + durable notifications · 0006 assessment core/web-first clients · 0007 grading/academic review · 0008 assignments/gradebook · 0009 live learning/web-first media · 0010 recording platform/storage tiers · 0011 analytics/operations · 0012 advanced academic platform · 0013 integration platform/interoperability.

> Historical note: ADRs 0010–0011 were duplicated during the 0.13/0.14 milestones; files were renumbered (advanced → 0012, integration → 0013) to restore chronological order. References to the old numbers may persist in older chat handoffs.

## Commercial (`commercial/`)
- `LICENSING_ARCHITECTURE.md` — subscription → entitlement → limit model.
- `OFFLINE_LICENSE_FORMAT.md` — Ed25519 signed offline license envelope (planned milestone).

## Operations (`operations/`)
- `IDENTITY_BOOTSTRAP.md` — first-owner bootstrap and session incident response.

## API
- `api/openapi.yaml` — OpenAPI 3.1 contract (server base `/api/v1`).
