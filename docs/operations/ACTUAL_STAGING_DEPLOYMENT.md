# UEP — actual remote staging deployment and qualification

This is the deployment authority for the real UEP product. The older root-level
`FREE_TIER_DEPLOYMENT.md` and `docs/CLOUDFLARE_DEMO_DEPLOY.md` described a local
presentation build and are not evidence of a functioning multi-tenant deployment.

## Required architecture

```
Browser
  └─ Cloudflare Pages: https://uep-frontend-testing.pages.dev
       ├─ VITE_LOCAL_MODE=false
       ├─ VITE_BACKEND_URL=https://uep-backend-testing.fly.dev
       └─ Keycloak OIDC: realm education / client platform-web
             └─ authorization-code flow + PKCE S256
                    │
                    ▼
             Fly.io Spring Boot API
               app: uep-backend-testing
               region: syd
                    │
                    ▼
             isolated Supabase PostgreSQL staging database
```

The frontend must not use `LocalExperience` or `LocalDemoStore` as the
product identity/data path. The backend must connect to PostgreSQL through
`SUPABASE_DB_URL`, `SUPABASE_DB_USER`, and `SUPABASE_DB_PASS`. The
`/api/v1/me` endpoint must resolve the authenticated Keycloak subject to an
active tenant membership and roles in that database.

## Current verified boundaries

- The repository CI workflow verifies backend Maven tests, a packaged-server
  startup/health smoke against a fresh PostgreSQL database, frontend lint/unit
  tests/build, repository policy, and dependency scanning.
- The local presentation Playwright suite is now a separate manual workflow.
  Its results are never product acceptance evidence.
- The real deployed-product E2E workflow is `.github/workflows/staging-e2e.yml`.
  It runs only after the staging environment has been configured and enabled.
- No successful Cloudflare Pages, Fly.io, Supabase, or Keycloak provider-side
  deployment was established by repository CI. A successful build is not a
  deployment, and a pages.dev URL alone is not proof of connected identity/API/DB.

## Provider configuration required before staging deployment

### Fly.io — backend app

The existing application configuration is `apps/platform-server/fly.toml`:

- Fly app: `uep-backend-testing`
- Region: `syd`
- Internal HTTP port: `8080`
- Image entry point: Spring Boot packaged JAR
- Profile: `production`

Set these Fly secrets in the Fly app (never commit their values):

| Secret | Requirement |
|---|---|
| `SUPABASE_DB_URL` | JDBC URL for the isolated staging Supabase PostgreSQL database |
| `SUPABASE_DB_USER` | Staging database user |
| `SUPABASE_DB_PASS` | Strong staging database password, at least 16 characters |
| `OIDC_ISSUER_URI` | Exact issuer URL for the deployed `education` realm |
| `OIDC_JWK_SET_URI` | Realm JWKS endpoint |
| `PLATFORM_RECORDING_PLAYBACK_SECRET` | Random secret, at least 32 characters |
| `PLATFORM_INTEGRATION_SECRET_KEY` | Random secret, at least 32 characters |
| `LIVEKIT_API_KEY` | Staging LiveKit API key, at least 16 characters |
| `LIVEKIT_API_SECRET` | Staging LiveKit API secret, at least 32 characters |
| `PLATFORM_DEV_MODE` | Must be `false` |

If storage is configured as S3, also set the platform's S3 endpoint, bucket,
region, access key, secret, and path-style settings required by the adapter.
Do not use the ephemeral Fly container filesystem as durable student content
or recording storage.

Before deployment, verify the Supabase project reference and database name are
the isolated staging target. Do not point Fly at an unknown or customer
production database. The Fly app has no persistent volume configured.

Deploy from the backend directory so the Dockerfile's `COPY pom.xml` and
`COPY src` paths resolve correctly:

```bash
cd apps/platform-server
flyctl deploy --remote-only --config fly.toml --dockerfile Dockerfile
```

Flyway applies the repository migrations on startup. Migrations V001–V046
must be applied against a fresh isolated staging database in CI before this
deployment is accepted. Do not manually baseline or edit Flyway history to
silence migration errors.

### Supabase PostgreSQL

Use a dedicated staging project/database, not a production database with a
different username. Verify the connection identity with a read-only query
before the first deployment:

```sql
SELECT current_database(), current_user, inet_server_addr(), version();
SELECT count(*) AS tenant_count FROM public.tenant;
SELECT version, description, success
FROM public.flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 10;
```

Compare the result to the expected staging project/database identity outside
the application. Never publish the connection URL or password in CI logs.

### Keycloak — real identity provider

The realm import source is `infra/keycloak/education-realm.json`:

- Realm: `education`
- Public web client: `platform-web`
- Authorization Code flow enabled
- PKCE S256 required
- Implicit flow disabled
- Direct access grants disabled
- The `tenant_id` user attribute is mapped into the access token.

Configure the deployed client with the exact frontend origin:

- Redirect URI: `https://uep-frontend-testing.pages.dev/*`
- Web origin: `https://uep-frontend-testing.pages.dev`

Keep the redirect allowlist exact. Add a custom domain only after its DNS and
TLS configuration are live. The current JSON source contains localhost
redirects for local compose development; that alone is not a production
Keycloak configuration.

The deployed frontend build needs:

- `VITE_LOCAL_MODE=false`
- `VITE_BACKEND_URL=https://uep-backend-testing.fly.dev`
- `VITE_KEYCLOAK_URL=<deployed Keycloak base URL>`
- `VITE_KEYCLOAK_REALM=education`
- `VITE_KEYCLOAK_CLIENT_ID=platform-web`

These are build-time Vite variables. Changing them in a runtime Cloudflare
secret store does not rewrite an already built JavaScript bundle; rebuild and
redeploy the frontend after changes.

### Cloudflare Pages — frontend

The target project name is `uep-frontend-testing`, with production branch
`main`, root/build directory `apps/web`, and build output `dist`.

Build and deploy only after the backend and Keycloak endpoints are configured:

```bash
cd apps/web
npm ci
# Set the five VITE_* variables above in the build environment.
npm run build
npx wrangler pages deploy dist --project-name uep-frontend-testing --branch main
```

The frontend's backend URL is not inferred from the Vite development proxy.
The deployed application calls `VITE_BACKEND_URL` directly and sends a
Keycloak bearer token. The API must return the matching CORS headers for the
Pages origin.

## Staging identity and data qualification

The staging account matrix is supplied to GitHub Actions as an environment
secret named `UEP_E2E_TEST_ACCOUNTS_JSON`. It must be a JSON object with these
keys:

`organization_owner`, `organization_administrator`,
`branch_administrator`, `academic_administrator`, `exam_controller`,
`finance_administrator`, `teacher`, `evaluator`,
`teaching_assistant`, `mentor`, `student`, `guardian`,
`support_operator`, and `auditor`.

Each value must contain `username`, `password`, `roleName`,
`displayName`, and `tenantId`. Store real test credentials only in the
GitHub `staging` Environment secret. Use non-customer accounts and test data.

Set these GitHub repository variables:

| Variable | Example |
|---|---|
| `UEP_STAGING_E2E_ENABLED` | `true` only after the environment is ready |
| `UEP_E2E_BASE_URL` | `https://uep-frontend-testing.pages.dev` |
| `UEP_E2E_BACKEND_URL` | `https://uep-backend-testing.fly.dev` |
| `UEP_E2E_KEYCLOAK_ISSUER` | Exact `https://.../realms/education` issuer |

The staging test workflow fails closed unless all endpoints are HTTPS and all
14 role accounts are present. It verifies:

1. The deployed frontend responds with HTML.
2. The deployed API health endpoint reports `UP`.
3. The deployed Keycloak OIDC discovery document reports the expected issuer.
4. Each of the 14 actual Keycloak accounts can authenticate.
5. Each account's frontend request to `/api/v1/me` succeeds against the
   configured backend and resolves the expected tenant, active membership,
   display name, and role from PostgreSQL.
6. The real application workspace renders without the local-mode banner.

This matrix is an identity/integration gate, not yet a complete feature
acceptance suite. Full product qualification must additionally seed and execute
the cross-role workflows below against staging data.

### Required coherent staging dataset

Seed at least two tenants, each with branches, and enough linked domain records
to exercise both positive and negative paths. The initial proposed account
population is 14 roles for the primary tenant, plus a secondary tenant with
its own owner, administrator, teacher, students, and guardians. Do not treat
this proposal as seeded data until row counts and API results are captured.

At minimum, prepare:

- Tenant, branch, user, membership, role assignment and organization settings.
- Academic periods, programs, courses, subjects, cohorts/batches, enrollments,
  teacher assignments and timetables.
- Classes, attendance, assignments, submissions, grading, assessments, exams,
  attempts, results and review records.
- Content metadata and real durable object storage uploads.
- Announcements, notifications, messaging conversations, participants,
  messages and read receipts.
- Live classes, presence, attendance evidence and a configured LiveKit service.
- Finance invoices, provider test payments, settlements and webhooks in a
  payment-provider sandbox.
- Audit entries, analytics/reporting records, API credentials and webhook
  subscriptions.
- Negative cases: cross-tenant and cross-branch IDs, unlinked guardians,
  unassigned teachers, disabled/suspended users, missing permissions and
  expired entitlements.

Seed only through an idempotent staging seeder that uses the application's
domain services or authenticated APIs. It must verify the database's staging
identity before writing, have a dry-run, use stable IDs, and never delete
unrelated records. Do not run blind SQL inserts to bypass domain invariants.

### Cross-role product acceptance

After the identity matrix passes, run remote browser/API workflows that cover:

1. Owner creates a branch and provisions a teacher and student.
2. Teacher creates and publishes an assignment; the student submits it.
3. Teacher grades the submission; the student signs in again and sees the
   persisted grade; the linked guardian sees only the linked learner's result.
4. Teacher records attendance; student and guardian see only permitted
   attendance.
5. Academic administrator creates/publishes an assessment; student attempts
   it; evaluator/exam controller review and publish results as authorized.
6. Finance administrator issues an invoice; payment is performed only through
   a configured provider sandbox and confirmed by a verified callback.
7. Content upload is retrieved from durable external storage after a new
   browser context and backend restart.
8. Live class join/leave, presence, attendance finalization and recording
   retrieval are verified with actual LiveKit/Egress services.
9. Announcement delivery and messaging are persisted, with tenant/branch
   boundaries verified.
10. Cross-tenant/branch negative requests are rejected by the backend, not
    merely hidden in the frontend.

For every write workflow, prove persistence after reload, a new browser
context, fresh sign-in and service restart. Capture API responses, database
row counts/IDs, audit events and test artifacts.

## Release acceptance

A deployment is accepted as a real UEP staging product only when all are true:

- Provider deployment records show the expected Cloudflare and Fly releases.
- Frontend static bundle has `VITE_LOCAL_MODE=false` and the expected backend
  and OIDC URLs.
- Keycloak redirects to the deployed frontend and issues the required
  `tenant_id` claim.
- Fly startup is healthy with the staging Supabase database; Flyway history
  matches the repository migrations.
- `/api/v1/me` succeeds for each test identity and returns database-backed
  membership and role data.
- The 14-role staging E2E suite passes.
- Cross-role feature workflows, persistence, uploads and isolation tests pass.
- No customer/production data is mixed with synthetic test data.

A successful local build, local Playwright run, demo login, HTTP 200 from the
static Pages root, or an isolated backend health endpoint alone does not meet
this acceptance bar.
