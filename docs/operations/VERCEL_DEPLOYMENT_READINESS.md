# Vercel deployment readiness — UEP

**Status:** configuration and local/CI contracts are in progress; no Vercel deployment has been requested or triggered in this work session.

This document describes the Vercel-first candidate separately from the existing Fly.io + Supabase + Keycloak staging architecture in `ACTUAL_STAGING_DEPLOYMENT.md`. A committed `vercel.json` is not evidence of a successful deployment, an active Vercel project, a provisioned database, or a functioning production identity path.


## Stop condition after the one-time deployment

The first deployment, when Vercel access is available, must be manually triggered
for the selected commit—not started as a continuing Git auto-deployment stream.
The project configuration now sets `git.deploymentEnabled=false`, which disables
automatic deployments from all Git branches. A later update must be manually
approved and initiated; pushes to `main` must not create additional deployments.

After the one-time deployment passes the health smoke test, pause the Vercel
project so the production deployment is blocked while the user is not actively
using it. Vercel documents that pausing a project blocks the active Production
Deployment; visitors will not be able to use the application until the project
is resumed. When the user is ready to use the service, resume it manually. Do
not delete the project or database just to stop metered runtime usage.

This stop condition is not executed now: no Vercel team or project is currently
visible to the connected account tools, and no Vercel deployment was started.
The stop actions are therefore a required post-deployment acceptance step, not
a claim that a project has already been paused.

## Candidate topology

- `frontend`: Vite application rooted at `apps/web`.
- `backend`: Spring Boot container rooted at `apps/platform-server`, built from its Dockerfile.
- Same-origin public routing: `/api/*` to the backend; all other paths to the frontend.
- `/api/_health` maps to Spring Actuator's health endpoint only.
- Frontend production builds must use `VITE_LOCAL_MODE=false`, real Keycloak OIDC values, and must not set `VITE_BACKEND_URL` when same-origin service routing is used.

The repository's build guard rejects local/demo mode and missing or non-HTTPS OIDC configuration when Vercel sets `VERCEL=1`. CI separately exercises the same production-mode environment verifier.

## Required runtime/build configuration

Frontend build-time values:

- `VITE_LOCAL_MODE=false`
- `VITE_KEYCLOAK_URL=https://<verified-issuer-host>`
- `VITE_KEYCLOAK_REALM=education`
- `VITE_KEYCLOAK_CLIENT_ID=platform-web`
- `VITE_BACKEND_URL` must be unset when the same-origin `/api/*` service rewrite is used.

Backend runtime values (Vercel project environment, never committed):

- `SPRING_PROFILES_ACTIVE=production,vercel`. The image defaults to `production`; the additional `vercel` profile applies `application-vercel.yaml`.
- `application-vercel.yaml` disables Flyway at runtime, so request-serving instances never run schema migrations during startup. Apply schema changes only through the explicit `UEP Database Migration` workflow and its dedicated migration credentials.
- Use a least-privilege runtime database role for the Spring datasource; keep schema-owner/DDL privileges in the separate migration role.
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`; alternatively the existing `SUPABASE_DB_URL`, `SUPABASE_DB_USER`, and `SUPABASE_DB_PASS` aliases.
- `OIDC_ISSUER_URI` and `OIDC_JWK_SET_URI` must match the deployed Keycloak realm exactly.
- `PLATFORM_RECORDING_PLAYBACK_SECRET` and `PLATFORM_INTEGRATION_SECRET_KEY`, each at least 32 characters and not equal to published development defaults.
- `PLATFORM_DEV_MODE=false`.
- Preserve database TLS verification; do not disable certificate verification to work around connectivity.

The application binds to `PORT` (default 8080). Do not expose actuator metrics, Prometheus, Modulith, or other management endpoints through public rewrites; the current health rewrite is intentionally restricted to `/actuator/health`.

## Runtime feature gates

A request-serving deployment must not pretend that local disk, LiveKit, or scheduled workers are durable platform services.

| Setting | Vercel candidate | Reason |
|---|---:|---|
| `PLATFORM_DEV_MODE` | `false` | Production secret/configuration checks remain active. |
| `PLATFORM_BACKGROUND_WORKERS_ENABLED` | `false` unless a separately qualified worker runtime is attached | The Spring scheduled pollers cover webhooks, notification outbox/email, announcement scheduling, cleanup, retention, and recordings. A request-serving instance is not accepted as a durable worker. |
| `PLATFORM_UPLOAD_STAGING_ENABLED` | `false` until shared durable staging is implemented and tested | Current chunk staging uses container-local filesystem state. |
| `PLATFORM_LIVEKIT_ENABLED` | `false` until LiveKit/Egress is provisioned and verified | Live-class start/join and recording request/stop fail closed while disabled. |

**Important workflow limitation:** disabling scheduled workers means worker-backed operations cannot be treated as complete. In particular, queued webhook delivery, email delivery, scheduled announcement transitions, object cleanup, retention jobs, and recording processing require a durable worker. A Vercel deployment without a separately verified worker service is therefore an API/frontend slice, not a fully qualified UEP deployment.

Vercel documents long-running workers separately under Daemons. The Vercel Services/container configuration does not by itself prove that a scheduled worker will remain alive or be billed within a free allowance. Daemon availability, quotas, and cost must be checked against the actual account before enabling these workflows. Do not switch scheduled workers on merely to make the feature set appear complete.

## Database qualification

- Do not point the Vercel candidate at the connected Supabase project until the database identity, role, RLS policies, and migration state have been qualified.
- A read-only catalog audit of the currently connected Supabase project found an event trigger that enables RLS on new `public` tables, while the application schema has no tenant-aware RLS policies/session context. Do not work around this by running the application as `postgres`, `supabase_admin`, or another broad `BYPASSRLS` role.
- A separate PostgreSQL provider (for example, a Vercel Marketplace Neon resource) is a candidate, not a provisioned or approved database. Confirm the selected plan, region, connection pooling, quotas, backups, and pricing before creating a resource.
- The explicit migration runner is `com.universalplatform.DatabaseMigrationRunner`; it validates and applies Flyway migrations only when invoked with dedicated migration credentials. Never run migrations automatically from request-serving Vercel instances.

## Vercel Marketplace database candidate: Neon Postgres

A Vercel-native Neon Marketplace resource is the preferred database candidate
for the Vercel-first profile. It keeps database provisioning and environment
connection under the Vercel project rather than reusing the currently connected
Supabase project. No Neon resource has been provisioned yet.

As checked on **November 2, 2026**, Neon's documented Free plan includes:

| Limit | Free allowance |
|---|---:|
| Compute | 100 CU-hours per project per month |
| Maximum autoscaling | 2 CU |
| Storage | 1 GB per project (20 GB account-wide) |
| Public network transfer | 5 GB per project per month |
| Branches | 10 per project |
| Idle suspend | 5 minutes |
| Instant restore history | 6 hours |

A 0.25-CU compute can use about 400 active hours from a 100-CU-hour allowance.
It is still a constrained staging/prototype database: a continuously busy
instance, larger tenants, file metadata growth, or high data transfer can
consume the included allowance. Neon's Free plan suspends compute when the
compute/transfer allowance is exhausted; writes that would exceed the storage
cap fail until capacity becomes available or the plan changes. Review the
current Dashboard quotas before provisioning and periodically during use.

References:
- [Vercel Marketplace — Neon](https://vercel.com/marketplace/neon)
- [Neon Free plan limits and quotas](https://neon.com/docs/introduction/plans)
- [Neon connection pooling](https://neon.com/docs/connect/connection-pooling)

### Connection contract for Spring Boot

The Vercel/Neon integration provides a pooled `DATABASE_URL` and an unpooled
`DATABASE_URL_UNPOOLED` connection. UEP currently expects a JDBC URL and
separate datasource username/password; it does not directly consume Neon's
raw `postgresql://` URL. Configure these Vercel runtime variables explicitly:

- `SPRING_DATASOURCE_URL`: the pooled connection translated to
  `jdbc:postgresql://...`, with TLS required.
- `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`: the
  dedicated least-privilege runtime role, not the schema-owner migration role.
- GitHub Environment `vercel-database-migrations` secrets:
  `UEP_DATABASE_MIGRATION_JDBC_URL` from the **direct/unpooled** connection,
  `UEP_DATABASE_MIGRATION_USERNAME`, and
  `UEP_DATABASE_MIGRATION_PASSWORD` for the schema-owner migration role.

Keep Hikari bounded (the current defaults are maximum pool size 5 and minimum
idle 0). Do not run Flyway through Neon's pooled transaction endpoint; invoke
the manually confirmed migration workflow with the direct connection instead.
The API runtime uses the pooled endpoint. The Vercel Spring profile disables
startup Flyway.

The Marketplace integration's existence does not itself configure Spring's
JDBC URL, create the least-privilege runtime role, or apply migrations. Those
steps remain explicit deployment gates. Do not place either connection string
in frontend Vite variables or commit it to Git.

## Account and deployment verification

The Vercel account connector did not return any teams or projects for this repository in the current session. Therefore these items remain **unverified**:

1. Whether the target account/team can create Vercel Services and container services.
2. Whether the target plan supports the required backend container and any Daemon/worker resources within the user's free-use requirement.
3. Whether an existing Vercel project is linked to `shreyashjagtap157/UEP`.
4. Whether any Marketplace database resource is available on a no-cost plan with sufficient limits.

No Vercel project was created, no Marketplace database was provisioned, and no deployment was started. Do not interpret the configuration commit or green CI jobs as a deployment.

## CI evidence and next gates

- `.github/workflows/ci.yml` tests PostgreSQL 17.11 and 18.4, runs backend tests, builds the backend container, starts the packaged application against a fresh database, and checks health/load behavior.
- The web job runs lint, frontend tests, and the production-mode build contract.
- Repository policy validates the Vercel route/config contract and deployment safety gates.
- The most recent observed run for commit `49c24455bf2163cc2c55f53f87369ddc0d4ee77a` was still running when this document was written. Re-check the Actions result before marking CI green.

Acceptance requires, at minimum: green PostgreSQL 17/18 CI, successful packaged-container smoke test, verified OIDC issuer/JWKS, verified least-privilege database connection and migration, durable storage, a worker execution design for asynchronous workflows, and real browser E2E tests against the deployed product. Demo/local Playwright results are not product acceptance evidence.

## References

- Vercel Services: https://vercel.com/docs/services
- Vercel container runtime: https://vercel.com/docs/functions/runtimes/container
- Vercel Daemons: https://vercel.com/docs/daemons
- Vercel Marketplace storage: https://vercel.com/docs/storage
