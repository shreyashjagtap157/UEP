# UEP SQLite Local Qualification — Compatibility Boundary

**Purpose:** provide a practical local database for development and a deliberately limited functional qualification path when PostgreSQL cannot run on the workstation.

## Current implementation

- Database profile: `local-sqlite`.
- Default identity profile: `local-native` (`local-sqlite,local-native`).
- Optional identity profile: `local-keycloak` (`local-sqlite,local-keycloak`), selected only with `-IdentityProvider Keycloak`.
- Driver: Xerial SQLite JDBC.
- Dialect: Hibernate community `SQLiteDialect`.
- Schema: Hibernate `ddl-auto=update` from JPA entity mappings.
- Metadata extraction: Hibernate `hibernate.hbm2ddl.jdbc_metadata_extraction_strategy=individually` so existing-table inspection runs per entity/table instead of one grouped metadata query across the full schema.
- Flyway: disabled only in this profile. The 50 PostgreSQL migrations are not executed or translated.
- Database file: `.uep-local/uep-local.sqlite` (ignored by Git).
- Connection pool: one connection for the local process.
- Foreign keys: enabled through SQLite connection initialization.
- Background workers: disabled in this profile.
- Keycloak: optional separate `dev-file` local development store under `.uep-local/tools/keycloak-26.8.2/data`; never shares the UEP SQLite file. It is used only by the `local-keycloak` profile.
- Production and CI database: PostgreSQL remains unchanged.

## SQLite schema metadata scaling

Xerial SQLite JDBC implements `DatabaseMetaData.getColumns()` by collecting column metadata for the matching tables. Hibernate's grouped schema-metadata strategy can ask for the complete mapped schema in one operation; with the UEP entity/table count and column count this can exceed SQLite's default compound-SELECT term limit during `ddl-auto:update` on a later startup.

The local SQLite profile therefore sets `hibernate.hbm2ddl.jdbc_metadata_extraction_strategy=individually`. Hibernate inspects each mapped entity/table separately. This keeps `ddl-auto:update` enabled, avoids increasing SQLite's global `SQLITE_LIMIT_COMPOUND_SELECT` to an unbounded value, and does not require deleting or recreating the database.

The backend integration test asserts the Hibernate strategy, requests JDBC column metadata for every existing non-internal SQLite table one table at a time, and starts a second real Spring Boot application context against the same database file to qualify the existing-schema startup path. Re-run the real application against the existing `.uep-local/uep-local.sqlite` after syncing the fix; do not delete the file as a workaround.

## Verified in CI so far

- The Spring Boot test context started using the SQLite profile.
- Hibernate generated the `tenant` table in SQLite.
- A tenant entity was persisted and read back.
- The SQLite-aware tenant existence check completed using the UUID binary representation.
- PostgreSQL 17.11 and 18.4 backend verification continued to pass on the same source line after the SQLite-specific test fix (see the exact CI run links in the session handoff).

This is backend integration-test evidence. It is not a Windows launcher/browser run.

## First native SQLite workflow

The default LocalNative SQLite browser test (`apps/web/test/native/real-sqlite.native.spec.ts`) verifies:

1. LocalNative-authenticated `GET /api/v1/me` and the persisted owner/tenant role.
2. The browser sends neither a local-auth header nor a bearer token; the Vite Node proxy injects the launcher credential server-side.
3. Branch creation through the authenticated browser/API path.
4. Browser reload and branch readback through the API.
5. SQLite file existence, size and 16-byte SQLite signature.
6. LocalNative sign-out and rejection of the revoked launcher session.

The separate Keycloak OIDC test (`apps/web/test/native/real-keycloak.native.spec.ts`) is selected explicitly with `-IdentityProvider Keycloak`. It validates discovery, Authorization Code + PKCE (S256), the authenticated bearer request to `/api/v1/me`, and the database-backed local owner tenant and role. Both tests use the same UEP SQLite compatibility boundary, but only one identity profile is active for a backend process.

The tests must be run on the same Windows user profile that initialized the protected UEP state. They read generated credentials only inside the test process and never print them.

## Not yet qualified on SQLite

| Area | Why it is not yet qualified |
|---|---|
| Full Flyway migration history and database callbacks | Migrations use PostgreSQL-only DDL, UUIDv7 functions, JSONB, triggers, partial indexes and other PostgreSQL behavior. |
| Database roles, grants and ownership | SQLite has no PostgreSQL role/schema privilege model. |
| Multi-writer and row-lock concurrency | SQLite uses file/database locking; it is not equivalent to PostgreSQL `SELECT ... FOR UPDATE`. |
| Scheduling, recording, webhook and notification workers | Several worker coordination paths use PostgreSQL advisory locks. Workers are disabled in this profile. |
| Membership and role administration | JPA paths are likely portable, but full UI/API and optimistic-lock behavior has not yet been tested under SQLite. |
| Analytics, engagement and survey/report paths | Some services use PostgreSQL-specific aggregate, JSONB or native SQL expressions. |
| Messaging, delivery, finance and quota workflows | Native repository queries and pessimistic locking paths need individual compatibility tests. |
| Storage cleanup, integration delivery and external provider workflows | Outbox processing, advisory locks, native SQL and worker semantics are PostgreSQL-qualified only. |
| Backup/restore, cross-tenant integrity and production resilience | PostgreSQL migrations, constraints, triggers and operational recovery are not represented by Hibernate-generated SQLite DDL. |

The list is a qualification boundary, not a claim that every listed endpoint definitely fails. A workflow becomes SQLite-qualified only after a test runs against the SQLite profile and proves its expected API behavior and persistence.

## Operating commands

SQLite (default local qualification, LocalNative identity):

```powershell
.\startuep.ps1 -Mode Real -Database SQLite
```

SQLite with real Keycloak OIDC:

```powershell
.\startuep.ps1 -Mode Real -Database SQLite -IdentityProvider Keycloak
```

PostgreSQL (full database-specific qualification when PostgreSQL is available):

```powershell
.\startuep.ps1 -Mode Real -Database PostgreSQL
```

Native SQLite browser test:

```powershell
Set-Location apps\web
npm run test:e2e:native
```

The native LocalNative browser test is the default SQLite workflow. The Keycloak OIDC browser test is run separately after starting with `-IdentityProvider Keycloak`. Both modes use a separate Keycloak dev-file store; neither makes SQLite equivalent to PostgreSQL. The native Playwright suite skips PostgreSQL-only grant checks in SQLite mode and skips SQLite-only checks in PostgreSQL mode.

## Safety and data separation

- Do not copy the SQLite file into production or use it as a PostgreSQL migration source.
- Do not point a production profile at the SQLite file.
- Do not describe SQLite test results as PostgreSQL qualification.
- Do not delete `.uep-local/uep-local.sqlite` unless intentionally resetting local qualification data and after confirming no process is using it.
- The SQLite file, Keycloak dev-file store, local accounts and protected bootstrap state are workstation-only.
