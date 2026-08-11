# ADR 0004: Flexible Academic Core and Explicit Participation Records

- Status: Accepted
- Milestone: 0.3.0.0

## Context

The product must serve institutions whose academic structures differ substantially. A school, university, coaching institute, and corporate-training deployment should not require separate source forks or mutually incompatible schemas.

The platform also needs stable participation records for later scheduling, attendance, assessments, gradebooks, credentials, and analytics.

## Decision

Use three domain modules: `academics`, `curriculum`, and `enrollment`.

Academic hierarchy links are optional where the business concept is genuinely optional. Curriculum modules have at most one direct parent among program, course, and subject. Batches are operational cohorts rather than another mandatory curriculum hierarchy level.

Represent learner participation using explicit enrollment records and teaching responsibility using explicit teacher-assignment records. Both records are tenant-owned, lifecycle-aware, audit-recorded, and constrained to exactly one academic target/scope.

Use composite tenant foreign keys for cross-table relationships. Use optimistic entity versions for edits and a pessimistic batch lock for count-based capacity allocation.

## Consequences

The model supports multiple institution types without client-specific schema forks. Later domains receive stable IDs and explicit participation relationships. The tradeoff is that application services must validate optional-parent consistency rather than relying on one fixed hierarchy path.
