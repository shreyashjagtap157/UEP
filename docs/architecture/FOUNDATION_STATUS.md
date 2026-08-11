# Platform Foundation Status

## Implemented through 0.2.0.0-SNAPSHOT

The engineering foundation now includes tenant-scoped identity and organization primitives required by later academic modules:

- Java 25 / Spring Boot / Spring Modulith application boundary;
- PostgreSQL 18 + Flyway schema evolution;
- OIDC authentication with trusted JWT tenant context;
- tenant membership, RBAC, custom roles, permission grants, and branch scoping;
- revocable platform session observations;
- organization branches/settings with optimistic concurrency;
- entitlement-driven commercial licensing and signed offline-license preparation;
- append-oriented audit foundation;
- React/TypeScript authenticated administrator workspace;
- reference Keycloak/PostgreSQL/optional Valkey deployment;
- CI, security scanning, repository policy, and proprietary commercial licensing controls.

## Release-gate state

The repository remains a snapshot until JDK 25 + Maven + PostgreSQL 18 and the web dependency toolchain execute all integration, build, migration, lint, vulnerability, and policy gates successfully. Do not create `v0.2.0.0` merely from static inspection.
