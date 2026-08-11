# Implementation Status

## Current development version

`0.3.0.0-SNAPSHOT` — Academic Core.

The source implementation for the 0.3 milestone is committed across the backend and web application. It remains a snapshot because this execution environment cannot run the complete JDK 25 / Maven / PostgreSQL 18 / dependency-backed web release gate.

## Implemented through 0.2 Identity and Organization

- modular Java/Spring server foundation, PostgreSQL/Flyway, OIDC resource-server security;
- trusted tenant context and tenant-scoped commercial entitlement/limit/usage model;
- signed-offline-license verification primitives and audit foundation;
- global external-identity-backed users and tenant memberships;
- persisted system/custom RBAC, tenant/branch assignments, last-owner protection, and current-identity authorization;
- revocable platform sessions and authentication-assurance reporting;
- tenant branches and organization defaults;
- authenticated React administration for identity, roles, organization, passkeys/OTP, and sessions;
- CI, dependency scanning, proprietary commercial repository controls, and reference deployment infrastructure.

## Implemented for 0.3 Academic Core

### Academics

- tenant-owned academic periods with planned/active/closed/archived lifecycle;
- optional period-bound academic programs with active/inactive/archived lifecycle;
- code uniqueness, date validation, optimistic concurrency, and audit events;
- explicit public read-only academic directory for other modules.

### Curriculum

- courses that may belong to a program or stand alone;
- subjects that may belong to a course or stand alone;
- curriculum modules that may attach to one program, course, or subject, or stand alone;
- database and service constraints preventing multiple simultaneous direct module parents;
- tenant-scoped list/filter/create/update APIs with optimistic concurrency;
- explicit public read-only curriculum directory rather than cross-module repository access.

### Enrollment and teaching

- batches/cohorts with optional academic-period, program, course, branch, section, date, capacity, and lifecycle metadata;
- parent-consistency and active-parent validation for batch scheduling;
- administrative batch visibility separated from general learner curriculum visibility;
- learner enrollment in exactly one program, course, or batch;
- active duplicate enrollment prevention and enrollment lifecycle history;
- pessimistic batch locking around capacity checks so concurrent enrollment cannot oversubscribe the final seat;
- teacher/evaluator assignment to exactly one program, course, subject, module, or batch;
- assignment roles for lead teacher, teacher, teaching assistant, mentor, and evaluator;
- administrative enrollment/assignment views protected by explicit permissions;
- `/enrollments/me` and `/teacher-assignments/me` self-scope derived from trusted current membership and requiring no tenant-wide roster permission.

### Tenant and database integrity

- composite `(tenant_id, id)` foreign keys across academic period, program, course, subject, module, batch, enrollment, teacher assignment, branch, and membership relationships;
- database checks for single-target enrollment and single-scope teaching assignment;
- partial unique indexes that reject active duplicate participation records;
- append-compatible historical lifecycle rather than destructive record replacement.

### Web product surface

- academic structure workspace for periods, programs, courses, subjects, modules, and authorized batch administration;
- enrollment and teaching administration workspace;
- administrator academic-core metrics;
- learner dashboard backed only by current-member enrollment APIs;
- teacher/mentor/evaluator dashboard backed only by current-member teaching APIs;
- OpenAPI 3.1 contract updated for all implemented 0.3 endpoints and permission keys.

### Verification encoded in the repository

- academic hierarchy integration tests including cross-tenant parent rejection;
- enrollment self-scope and batch-capacity integration tests;
- teacher-assignment self-scope integration tests;
- existing identity, authorization, organization, tenancy, licensing, and session tests remain part of Maven verification;
- Spring Modulith, PostgreSQL 18 service CI, web lint/build, repository policy, and OSV dependency gates remain enabled.

## Verification available in this runtime

The local consolidation pass includes repository version/policy checks, whitespace checks, JSON/XML/YAML parsing, source-level Java syntax diagnostics, TypeScript diagnostics to the extent possible without installed dependencies, Git object integrity, and clean-history checks.

## Environment-limited qualification

This runtime currently has JDK 21 and Node.js but no Maven, Docker/Podman, locally installed project dependencies, or usable dependency-network path for the complete build. Therefore it cannot truthfully claim:

- JDK 25 Maven compilation/test success;
- Flyway execution against PostgreSQL 18;
- Spring application boot against PostgreSQL 18;
- dependency-backed React/Keycloak/TanStack lint and Vite production build;
- online OSV dependency resolution;
- complete CI success.

For that reason no `v0.3.0.0` release tag is created here. The repository remains `0.3.0.0-SNAPSHOT` until those gates pass on the required toolchain.

## Next roadmap milestone after qualification

`0.4.0.0` — Scheduling and Communication: calendar, recurrence, classes, conflicts, announcements, in-app notifications, email, notification preferences, and the universal Today dashboard.
