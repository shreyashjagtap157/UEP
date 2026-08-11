# Academic Core Architecture

## Scope

Milestone `0.3.0.0` establishes the canonical academic structure used by scheduling, content, assessments, grading, attendance, credentials, and analytics in later milestones.

The model is deliberately flexible. It supports schools, universities, coaching institutes, corporate training, and smaller organizations without forcing every deployment through one rigid hierarchy.

## Domain modules

The academic core is split into three Spring Modulith modules with explicit read-only directory boundaries between them:

- `academics` owns academic periods and programs;
- `curriculum` owns courses, subjects, and curriculum modules;
- `enrollment` owns batches/cohorts, learner enrollments, and teacher assignments.

Modules do not access another module's repositories. Cross-module validation uses public directory interfaces and keeps repository/model ownership inside the originating module.

## Flexible hierarchy

The canonical structure permits, but does not require, the following chain:

`Academic Period -> Program -> Course -> Subject -> Module`

A course may be standalone. A module may attach directly to a program, course, or subject, or remain standalone. A program may optionally be period-bound. This keeps the internal model compatible with organizational forms where one or more hierarchy levels do not exist.

A curriculum module can have at most one direct parent. PostgreSQL enforces that invariant with a `num_nonnulls(...) <= 1` constraint.

## Batches and cohorts

A batch is an operational cohort and may reference an academic period, program, course, and branch when those dimensions are relevant. Batch references are tenant-aware composite foreign keys. Batch scheduling validates that referenced parents and branches are currently usable.

Batch capacity is enforced inside the enrollment transaction. Batch-target enrollment obtains a pessimistic row lock before counting active enrollments, preventing two concurrent requests from independently observing the same last available seat and both succeeding.

Tenant-wide batch listing requires enrollment-view authority. A learner's or teacher's self dashboard does not gain roster-wide access merely because that person can read general curriculum.

## Learner enrollment

An enrollment belongs to exactly one tenant membership and exactly one of:

- program;
- course;
- batch.

The database and service layer both enforce this single-target invariant. Active duplicate enrollments are prevented with target-specific partial unique indexes.

Lifecycle states are:

- `ENROLLED`;
- `COMPLETED`;
- `WITHDRAWN`;
- `CANCELLED`.

Administrative enrollment views require explicit enrollment permissions. `/api/v1/enrollments/me` is separately self-scoped through the authenticated membership and cannot be redirected to another membership by request input.

## Teacher assignments

A teaching assignment belongs to exactly one active tenant membership and exactly one academic scope:

- program;
- course;
- subject;
- curriculum module;
- batch.

Roles currently include lead teacher, teacher, teaching assistant, mentor, and evaluator. Active duplicate assignments for the same person/scope/role are prevented by database constraints in addition to service validation.

Administrative teaching-assignment views require explicit authority. `/api/v1/teacher-assignments/me` is self-scoped independently of tenant-wide teaching-assignment permissions.

## Tenant integrity

Academic tables carry `tenant_id`, and relationships use composite `(tenant_id, id)` references. This prevents a row owned by Tenant A from referring to an academic record owned by Tenant B even if a future application defect supplies a foreign UUID.

Service lookups also derive tenant identity from the trusted authentication context. Tenant identifiers supplied by browser headers or request bodies are never authoritative.

## Concurrency and history

Editable academic entities carry optimistic versions. Updates require the caller's expected version and reject stale changes instead of silently overwriting concurrent edits.

High-contention capacity allocation additionally uses database row locking because optimistic entity versioning alone cannot safely enforce a count-based batch-capacity invariant.

Status fields preserve academic records rather than encouraging destructive deletion. Later assessment and grading modules can therefore retain stable references to historical curriculum structures.

## Dashboards

The web client exposes three role-aware academic views from the same API platform:

- administrators see structural counts and academic management workspaces;
- learners see only their own enrollments;
- teachers/mentors/evaluators see only their own teaching assignments.

The browser composes these APIs for presentation. Authorization and self-scope guarantees remain server-side.

## Future dependencies

Later modules should consume these domains through explicit public APIs/directories rather than repositories. In particular:

- scheduling references batches, academic scopes, and teacher assignments;
- content attaches learning resources to curriculum scopes;
- assessments reference versioned academic/curriculum scopes;
- gradebook and analytics derive learner populations from enrollments;
- attendance derives expected participants from batches/enrollments and teaching assignments.
