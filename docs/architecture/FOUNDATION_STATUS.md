# 0.1.0.0 Engineering Foundation Status

## Implemented in source

- monorepo/repository structure
- Java 25 / Spring Boot 4.1.0 build definition
- Spring Modulith 2.1.0 application boundary
- PostgreSQL 18 schema + Flyway migration
- tenant model and trusted request tenant context
- OIDC resource-server security foundation
- entitlement-driven commercial licensing model
- subscription lifecycle and feature/limit keys
- append-oriented audit foundation
- OpenAPI 3.1 contract seed
- React 19 / TypeScript 6 / Vite 8 web foundation
- reference PostgreSQL / Keycloak / optional Valkey Compose services
- CI definitions and repository policy checks
- proprietary source/license notice and commercial licensing ADR/docs

## Release-gate state

The source is intentionally `0.1.0.0-SNAPSHOT` until all gates are executed on a
machine with JDK 25, Maven, Docker/Podman, and dependency-network access.
Do not create the `v0.1.0.0` release tag before those gates pass.
