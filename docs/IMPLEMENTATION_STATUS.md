# Implementation Status

## Current development version

`0.2.0.0-SNAPSHOT` — Identity and Organization.

The source implementation for the 0.2 milestone is present and committed. It remains a snapshot because this execution environment cannot run the complete JDK 25 / Maven / PostgreSQL 18 / dependency-backed web qualification gate.

## Implemented through 0.1 foundation

- modular Java/Spring server foundation, PostgreSQL/Flyway, OIDC resource-server security;
- trusted tenant context and tenant-scoped commercial entitlement/limit/usage model;
- signed-offline-license verification primitives and audit foundation;
- React/TypeScript/Vite client foundation, reference infrastructure, CI, dependency scanning, and proprietary commercial repository controls.

## Implemented for 0.2 Identity and Organization

### Identity

- global external-identity-backed user accounts;
- tenant memberships with active/suspended/ended lifecycle;
- a person can hold multiple simultaneous roles;
- persisted built-in role catalog and custom roles;
- persisted permission grants and tenant/branch role assignment scopes;
- permission-based authorization service with no commercial plan-name checks;
- platform-super-admin controlled first-owner bootstrap;
- last-active-organization-owner lockout protection;
- paginated membership, role, role-assignment, and session APIs;
- current identity/permission endpoint;
- observed OIDC sessions with self/admin revocation and audit events;
- authentication-assurance extraction from signed `acr`/`amr` claims.

### Organization

- tenant branches with unique tenant-local code, lifecycle, timezone, and optimistic concurrency;
- organization defaults for timezone, locale, first weekday, and support contact;
- tenant-scoped branch queries and database constraints preventing foreign-tenant relationships.

### Strong authentication and web administration

- reference Keycloak realm configured for Authorization Code + PKCE, TOTP, WebAuthn/passwordless passkeys, and tenant claim mapping;
- authenticated React administration workspace for people, custom roles, branches, organization settings, passkey/OTP enrollment, identity account management, and platform-session revocation;
- bearer tokens remain in the Keycloak adapter's runtime lifecycle and business authorization remains server authoritative.

### Verification encoded in the repository

- identity/RBAC integration tests;
- multi-role teacher+student identity test;
- unprivileged custom-role denial test;
- session-revocation integration test;
- organization cross-tenant isolation test;
- existing tenant/licensing isolation tests updated for active membership enforcement;
- Spring Modulith, repository policy, web lint/build, PostgreSQL-backed Maven verification, and OSV dependency scan in CI.

## Verification executed here

Passed or inspected locally:

- repository version/policy consistency;
- `git diff --check`;
- JSON, XML, and YAML syntax checks;
- Java source syntax scan to the extent possible without Spring/JPA dependencies;
- TypeScript strict compiler invocation, which identified and led to correction of a local `RequestInit.signal` exact-optional-property issue;
- Git object integrity and clean committed-history checks at packaging time.

## Environment-limited qualification

This runtime has JDK 21 and Node.js but no Maven, Docker/Podman, installed project dependencies, or usable dependency-network access. Therefore it cannot truthfully claim:

- JDK 25 Maven compilation/test success;
- Flyway execution against PostgreSQL 18;
- Spring application boot against the production database engine;
- dependency-backed React/Keycloak/TanStack TypeScript/Vite build;
- online OSV vulnerability resolution;
- complete CI success.

For that reason no `v0.2.0.0` release tag is created here. The repository stays `0.2.0.0-SNAPSHOT` until those gates pass on the required toolchain.

## Next roadmap milestone after qualification

`0.3.0.0` — Academic Core: academic periods, programs, courses, subjects, modules, batches, enrollments, teacher assignments, and role-specific dashboards.
