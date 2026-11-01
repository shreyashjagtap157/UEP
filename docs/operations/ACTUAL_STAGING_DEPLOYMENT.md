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

#### Durable object storage — recommended Supabase Storage S3 adapter

The existing UEP backend already provides an S3-compatible adapter. For this
staging project, use the managed Supabase Storage S3 endpoint rather than the
local filesystem or an unenabled Cloudflare R2 account.

Before configuring Fly:

1. In Supabase Dashboard, open Storage → Configuration → S3, enable S3
   protocol access, and create a private bucket named
   `uep-staging-assets`.
2. Generate a dedicated S3 access-key pair for the UEP backend. Copy the
   endpoint, region, Access Key ID and Secret Access Key into the approved
   secret manager. The secret key is shown once.
3. The documented direct storage endpoint format is
   `https://<project-ref>.storage.supabase.co/storage/v1/s3`. For this
   project, the expected endpoint is
   `https://rykmwcslbzmkzmnnzvxm.storage.supabase.co/storage/v1/s3`.
   Use the region shown by the S3 configuration page; the current project
   region is `ap-southeast-1`.
4. Set the following Fly application configuration (the S3 credentials are
   secrets and must never be stored in GitHub variables, Pages variables, or
   frontend bundles):

   | Variable | Value |
   |---|---|
   | `PLATFORM_STORAGE_S3_ENABLED` | `true` |
   | `PLATFORM_STORAGE_S3_ENDPOINT` | Direct Supabase Storage S3 endpoint above |
   | `PLATFORM_STORAGE_S3_REGION` | Exact region shown in Supabase S3 settings |
   | `PLATFORM_STORAGE_S3_BUCKET` | `uep-staging-assets` |
   | `PLATFORM_STORAGE_S3_ACCESS_KEY` | Generated S3 Access Key ID (secret) |
   | `PLATFORM_STORAGE_S3_SECRET_KEY` | Generated S3 Secret Access Key (secret) |

The S3 adapter uses path-style addressing, as required by Supabase's
documented S3 client configuration. Supabase-generated S3 credentials grant
full S3 operations across all buckets and bypass Storage RLS. They are for
trusted server-side use only. Keep the bucket private; all downloads must
continue through UEP's authenticated backend authorization path.

After Fly secrets and configuration are set, an active tenant administrator
must configure that tenant's storage binding as `S3_COMPATIBLE` through the
UEP storage integration API/UI. Upload finalization writes an object key
containing the tenant identifier. Do not assume a global adapter is usable by
a tenant until its binding is configured.

The Supabase project's Free plan and Storage limits must be checked in the
Dashboard before setting the production upload-size limit. The current
application default is 5 GiB, while the adapter currently performs a single
S3 `PutObject`; qualify the provider's maximum object size and the app's
configured upload cap together. Validate a real upload, download, checksum,
tenant isolation, and retrieval after backend restart before marking storage
ready.

Do not use the ephemeral Fly container filesystem as durable student content
or recording storage. Local staging files are temporary upload state only.

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

The local-development realm remains `infra/keycloak/education-realm.json`.
The staging deployment has a separate, restricted import file:
`infra/keycloak/education-realm-staging.json`. It configures:

- Realm: `education`
- Public web client: `platform-web`
- Authorization Code flow with PKCE S256
- Implicit flow and direct access grants disabled
- `tenant_id` user attribute mapped into ID/access/userinfo tokens
- Redirect URI only: `https://uep-frontend-testing.pages.dev/*`
- Web origin only: `https://uep-frontend-testing.pages.dev`

The optimized Keycloak 26.7 container and provisional Fly application
configuration are now in `infra/keycloak/Dockerfile` and
`infra/keycloak/fly.toml`. The configuration expects the app name
`uep-keycloak-testing`, region `syd`, public port 8080 and private
management/health port 9000. The region and app-name availability are
provisional; inspect the authenticated Fly account, existing apps, capacity,
and price before creating the app or machine. Do not deploy the development
`start-dev` command from `infra/compose.yaml`.

#### Separate Keycloak database ownership

After the PostgreSQL compatibility/upgrade decision and before starting
Keycloak, create a dedicated `uep_keycloak` login role with a strong generated
password, no superuser/createdb/createrole/BYPASSRLS privileges, and a separate
`keycloak` schema owned by that role. Grant only database CONNECT and the
schema/table/sequence privileges required for Keycloak's own migrations.
Configure Keycloak with `KC_DB=postgres`, `KC_DB_SCHEMA=keycloak`,
`KC_DB_USERNAME=uep_keycloak`, `KC_DB_PASSWORD` as a Fly secret, and a JDBC
URL for the staging database. Do not use `postgres`, `supabase_admin`, or
the UEP application schema as Keycloak's database owner. Store no password in
the repository.

#### Keycloak Fly runtime configuration

The container enables health and metrics support during image build. Set
these runtime values through Fly configuration/secrets:

| Variable | Value |
|---|---|
| `KC_DB` | `postgres` |
| `KC_DB_SCHEMA` | `keycloak` |
| `KC_DB_URL` | Staging Supabase JDBC URL with TLS required |
| `KC_DB_USERNAME` | `uep_keycloak` |
| `KC_DB_PASSWORD` | Dedicated role password; Fly secret |
| `KC_DB_POOL_MIN_SIZE` | `2` |
| `KC_DB_POOL_MAX_SIZE` | `10` |
| `KC_HOSTNAME` | `https://uep-keycloak-testing.fly.dev` |
| `KC_HOSTNAME_STRICT` | `true` |
| `KC_HOSTNAME_STRICT_HTTPS` | `true` |
| `KC_HTTP_ENABLED` | `true` behind Fly's HTTPS proxy |
| `KC_HTTP_PORT` | `8080` |
| `KC_HTTP_MANAGEMENT_PORT` | `9000` |
| `KC_PROXY_HEADERS` | `xforwarded` |
| `KC_BOOTSTRAP_ADMIN_USERNAME` | Unique bootstrap administrator name |
| `KC_BOOTSTRAP_ADMIN_PASSWORD` | Strong one-time bootstrap password; Fly secret |

The bootstrap admin password and database password must never be committed or
placed in GitHub variables. Secure the Keycloak administrator account with
MFA, then remove or rotate bootstrap access according to Keycloak's admin
recovery guidance. Database TLS must be enabled; use full server verification
when the Supabase CA chain is available to the deployment.

Deploy from `infra/keycloak` only after the Fly account/app/region and
database schema are verified:

```bash
cd infra/keycloak
flyctl deploy --remote-only --config fly.toml --dockerfile Dockerfile
```

The intended issuer is
`https://uep-keycloak-testing.fly.dev/realms/education`; its discovery
document is
`https://uep-keycloak-testing.fly.dev/realms/education/.well-known/openid-configuration`,
and JWKS is
`https://uep-keycloak-testing.fly.dev/realms/education/protocol/openid-connect/certs`.
Do not configure Cloudflare or the backend with these values until the app is
deployed and the issuer/discovery/JWKS endpoints are verified.

The deployed frontend build needs:

- `VITE_LOCAL_MODE=false`
- `VITE_BACKEND_URL=https://uep-backend-testing.fly.dev`
- `VITE_KEYCLOAK_URL=https://uep-keycloak-testing.fly.dev`
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


## Live staging investigation checkpoint — 2026-11-01

This checkpoint records provider-side evidence gathered against the connected
GitHub, Cloudflare and Supabase accounts. It does not represent an accepted
staging deployment.

### Repository and CI

- Audited repository head: `94eab5c90d8be6c08e1695d6b58a849668e0834b`.
- GitHub Actions run `41328384790` (UEP CI) completed successfully on this
  commit. Backend verification and packaged-server health smoke, web lint/test/
  build, repository policy, and vulnerability scan all passed.
- The two staging E2E workflow runs for this commit completed as `skipped`.
  No live staging identity or product workflow was qualified.
- The migration directory contains V001–V046. V046 adds
  `upload_session.object_prefix`; this runbook's migration ceiling is therefore
  V046, not V045.

### Database compatibility blocker

The connected Supabase project is:

- Project: `UEP`
- Project reference: `rykmwcslbzmkzmnnzvxm`
- Region: `ap-southeast-1`
- Database: `postgres`
- Server version: PostgreSQL `17.11`

The repository's CI service container is PostgreSQL `18.4`. V001 uses
`uuidv7()` in column defaults; the same built-in is referenced in later
migrations (including V005, V009, V017, V019, V021, V022, V028, V029 and
V042–V043). The connected PostgreSQL 17.11 server has no
`pg_catalog.uuidv7()`, `public.uuidv7()`, or `extensions.uuidv7()`
function, and no UUIDv7 extension is available in the project's installed
extension catalog. PostgreSQL documents `uuidv7()` as a PostgreSQL 18
addition: https://www.postgresql.org/docs/18/release-18.html

Consequently, the present V001–V046 chain is not qualified for this PostgreSQL
17.11 target and will fail before the application schema is established.
Do not start Flyway or create an ad hoc UUID shim to bypass this mismatch.
Resolve it by verifying whether this Supabase project can be upgraded to a
compatible PostgreSQL 18 service, or by making and testing a deliberate
PostgreSQL 17 compatibility change in the repository. Supabase documents its
project-upgrade process and downtime considerations here:
https://supabase.com/docs/guides/platform/upgrading
Do not assume a PostgreSQL 18 upgrade option is available until the target
version and project eligibility are confirmed in the dashboard.

At audit time the project had zero public application tables, no application
Flyway history table, zero Auth users, zero Storage buckets and zero Storage
objects. Supabase-managed schemas (`auth`, `storage`, `realtime`,
`vault`) are present; this is not an empty physical database. The project is
the only UEP project visible to the connected Supabase account, but its
project name alone is not independent proof of an environment designation.
Keep it uninitialized until staging isolation and the PostgreSQL version
decision are confirmed.

### Supabase security-advisor remediation

The Supabase security advisor initially reported that
`public.rls_auto_enable()` was executable by `PUBLIC`, `anon` and
`authenticated` despite being a `SECURITY DEFINER` event-trigger function.
A Supabase migration named
`restrict_rls_auto_enable_function_execute` (version
`20261101175327`) revoked execution from `PUBLIC`, `anon`,
`authenticated` and `service_role`. A follow-up catalog query confirmed that
the `ensure_rls` event trigger remains enabled, the function remains owned by
`postgres`, and only `postgres` retains direct EXECUTE privilege. The
security advisor subsequently returned zero lints. This Supabase-side ACL
migration is separate from UEP's application Flyway history. Recheck the
advisor and trigger behavior after any database major-version upgrade.

### Cloudflare Pages

The `uep-frontend-testing` Pages project is linked to
`shreyashjagtap157/UEP`, production branch `main`, root `apps/web`,
build command `npm ci --ignore-scripts && npm run build`, and output
`dist`. Automatic production and preview deployments are disabled.
Both environments have `VITE_LOCAL_MODE=false`, the backend URL, realm and
client ID configured, but `VITE_KEYCLOAK_URL` is missing.

The newest recorded Pages production deployment at audit time was created
2026-11-01 06:11 UTC from commit
`c9352ea789cf5a3e9e6938e46d9803881a990a5b`, with deployment metadata
`commit_dirty=true`; the audited GitHub `main` was 19 commits ahead.
An unauthenticated request to the Pages hostname returned the local
presentation-mode screen. The hosted response therefore does not prove that
the currently configured production variables were used to build its assets.
Do not enable automatic deployments or publish a replacement until the
backend and Keycloak issuer are verified and the build is known to use the
correct commit and configuration.

### Backend, Keycloak, storage and Fly.io

- An unauthenticated request to
  `https://uep-backend-testing.fly.dev/actuator/health` timed out. No live
  backend health response or active Fly release was established in this
  checkpoint.
- The repository Keycloak realm file and `infra/compose.yaml` describe the
  local development Keycloak service only. No deployed staging Keycloak host,
  issuer, discovery document or JWKS endpoint was established. The Pages
  build cannot be completed for real authentication without this information.
- The application configuration defaults storage to local disk and LiveKit
  to localhost. No durable external object-storage or hosted LiveKit/Egress
  configuration was verified. The Fly app has no durable application volume
  in the documented architecture; do not rely on its container filesystem.
- The previous browser investigation observed `uep-backend-testing` in
  `sjc`, while repository `fly.toml` specifies `syd`. The current Fly
  dashboard and machine state remain unverified; inspect the existing app,
  machine, release, secrets by name/presence, networking and volumes before
  any resume, redeploy, region change or replacement. Never expose secret
  values.



### Database role and RLS boundary

A read-only role inventory found no UEP-specific login role in the connected
project. The connected SQL session used `postgres`; that Supabase-managed
login has `BYPASSRLS=true`. `supabase_admin` is a superuser, while
`service_role` also has `BYPASSRLS=true` but is not a login role. This
inventory does not reveal the Fly secret's value or establish which username
Fly is configured to use. Do not infer the backend username from the SQL
connector session.

The application schema has not been created, so there are no application
tables or application RLS policies to inspect yet. The automatic
`ensure_rls` trigger enables RLS on new public tables; that alone is not proof
of tenant isolation for backend queries. A repository search of the reviewed
security and migration sources did not establish a per-tenant database
session-variable/policy contract. Before creating application tables, decide
and document the migration-owner/runtime-role arrangement, the expected
grants, and whether tenant isolation is enforced at the database layer,
application service layer, or both. Any runtime role with `BYPASSRLS` or
table ownership can bypass ordinary RLS unless the table is forced through
RLS. Validate the actual Fly database identity and verify tenant-boundary
requests against the running backend; do not weaken RLS to resolve permissions.

After the function ACL change, a transaction-scoped probe created a temporary
public table, verified that `ensure_rls` enabled `relrowsecurity`, and
dropped the table in the same SQL block. The probe passed. The Supabase
security advisor returned zero lints after the ACL remediation.

### Current deployment gate

Do not initialize application tables, deploy a new Fly release, enable Pages
production deployment automation, enable staging E2E, or create persona
credentials until all of the following are true:

1. The Supabase target is conclusively identified as isolated staging and its
   PostgreSQL version is compatible with the full V001–V046 chain.
2. The database access roles and ownership model are established so the
   application does not accidentally run as a broad `BYPASSRLS` role.
3. The existing Fly resource and its region discrepancy are resolved without
   destroying an unknown resource.
4. A real staging Keycloak issuer, discovery/JWKS endpoints and client
   redirect/origin configuration are verified.
5. Strong production secrets, durable external object storage and any
   required LiveKit services are configured.
6. Cloudflare is rebuilt from the audited `main` SHA with
   `VITE_LOCAL_MODE=false` and all five verified Vite variables.
7. Synthetic test identities and tenant fixtures are prepared through the
   application's supported provisioning path.

After these prerequisites, execute Flyway, deploy backend then frontend, and
run the identity matrix and cross-role product workflows. Re-run CI against
any repository changes and record provider release IDs and persisted E2E
evidence before claiming staging acceptance.
