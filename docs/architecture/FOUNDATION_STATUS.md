# Platform Foundation Status

## Implemented through 0.5.0.0-SNAPSHOT

The engineering foundation now includes the tenant, identity, academic, scheduling, and communication primitives required by later learning, assessment, media, attendance, and finance modules:

- Java 25 / Spring Boot / Spring Modulith application boundary;
- PostgreSQL 18 + Flyway schema evolution;
- OIDC authentication with trusted JWT tenant context;
- tenant membership, RBAC, custom roles, permission grants, and tenant/branch scopes;
- revocable platform sessions and organization branches/settings;
- academic periods/programs, flexible course/subject/module curriculum, batches, enrollments, and teaching assignments;
- finite timezone-aware recurring scheduling with materialized occurrences and conflict controls;
- stable class-session anchors for later live-class, attendance, and recording domains;
- targeted/scheduled announcements with publication-time audience snapshots and acknowledgements;
- transactional notification outbox, in-app inbox, optional SMTP delivery, per-event preferences, bounded retries, and dead-letter visibility;
- universal Today dashboard with personal and scope-aware administrative views;
- entitlement-driven commercial licensing and signed offline-license preparation;
- append-oriented audit foundation;
- React/TypeScript authenticated workspaces for administration, academics, schedule, announcements, notifications, and Today;
- reference Keycloak/PostgreSQL/optional Valkey deployment;
- CI, security scanning, repository policy, and proprietary commercial licensing controls.

## Release-gate state

The repository remains a snapshot until JDK 25 + Maven + PostgreSQL 18 and the web dependency toolchain execute all integration, build, migration, lint, vulnerability, and policy gates successfully. Do not create `v0.5.0.0` merely from static inspection.
