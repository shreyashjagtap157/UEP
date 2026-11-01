# Vercel deployment readiness — UEP

**Status:** configuration and local/CI contracts are in progress; no Vercel deployment has been requested or triggered in this work session.

This document describes the Vercel-first candidate separately from the existing Fly.io + Supabase + Keycloak staging architecture in `ACTUAL_STAGING_DEPLOYMENT.md`. A committed `vercel.json` is not evidence of a successful deployment, an active Vercel project, a provisioned database, or a functioning production identity path.

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

- `SPRING_PROFILES_ACTIVE=production` (already the Docker image default).
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
