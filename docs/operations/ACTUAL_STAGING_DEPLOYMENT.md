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
  It is **manual-dispatch-only**: routine CI completion or merges to `main` do
  not start staging tests or synthetic writes. An operator must dispatch the
  workflow from the trusted `main` branch with `confirm_staging_e2e=yes`
  (the default is `no`); dispatches from feature/PR branches are rejected so
  unreviewed workflow revisions cannot receive staging Environment secrets.
  Configure the GitHub `staging` Environment with required reviewers and
  restrict its deployment branches to `main` before adding any staging
  secrets or enabling the workflow. This is a mandatory, server-enforced
  security boundary: the `github.ref == 'refs/heads/main'` job condition is
  defense in depth only, because manual dispatch selects the workflow revision
  from the requested ref. A modified non-main workflow revision could remove
  its own condition; only the Environment deployment-branch policy prevents
  that revision from receiving Environment secrets. Do not enable staging
  until the main-only Environment restriction has been verified in GitHub
  Settings. This repository connection cannot verify that external setting.
  The job also requires `UEP_STAGING_E2E_ENABLED=true`. Product-data writes
  require a second per-run input, `authorize_synthetic_writes=yes` (default
  `no`), and the repository variable `UEP_STAGING_SEED_DRY_RUN=false`.
  The workflow sets dry-run mode to false only when both of those gates pass;
  otherwise it forces dry-run. The confirmation secret is checked later by
  the seeder as a separate hard-stop before writes. If the first two gates
  enable write mode but the secret is missing or incorrect, the seeder aborts
  rather than silently switching back to dry-run. Treat the secret as an
  additional authorization check, not as an input to the workflow expression.
  Re-dispatch and explicit authorization are required for every staging write
  run. The staging Playwright configuration disables
  browser trace and screenshot retention because those can capture OIDC state,
  bearer headers, login fields, or tenant data. The workflow uploads HTML/JUnit
  test reports only; do not re-enable traces/screenshots without a reviewed
  redaction and restricted-artifact design.
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
| `SUPABASE_DB_URL` | JDBC URL for the isolated staging Supabase PostgreSQL database; must use `sslmode=require`, `verify-ca`, or `verify-full` in the production profile |
| `SUPABASE_DB_USER` | Staging database user |
| `SUPABASE_DB_PASS` | Strong staging database password, at least 16 characters |
| `OIDC_ISSUER_URI` | Exact issuer URL for the deployed `education` realm |
| `OIDC_JWK_SET_URI` | Realm JWKS endpoint |
| `PLATFORM_RECORDING_PLAYBACK_SECRET` | Random secret, at least 32 characters |
| `PLATFORM_INTEGRATION_SECRET_KEY` | Random secret, at least 32 characters |
| `LIVEKIT_URL` | Public secure LiveKit client endpoint, `wss://...` (not localhost) |
| `LIVEKIT_API_URL` | Public secure LiveKit server API endpoint, `https://...` (not localhost) |
| `LIVEKIT_API_KEY` | Staging LiveKit API key, at least 16 characters |
| `LIVEKIT_API_SECRET` | Staging LiveKit API secret, at least 32 characters |
| `PLATFORM_DEV_MODE` | Must be `false` |
| `PLATFORM_STAGING_DATABASE_IDENTITY_ENABLED` | Set to `true` only on the isolated staging Fly app to expose the permission-gated seed safety check |
| `PLATFORM_STAGING_EXPECTED_DB_HOST` | Exact PostgreSQL hostname of the isolated staging Supabase project; must match the live JDBC connection metadata |
| `PLATFORM_STAGING_EXPECTED_DB_NAME` | Exact staging database name (normally `postgres`); must match the live JDBC connection metadata |

Tenant provider selection is **per tenant**, not a single global provider
setting. An authorized tenant administrator selects one configured provider
and its object prefix through the external-storage binding. The upload service
resolves that tenant binding server-side and snapshots the provider/prefix in
the upload session; clients cannot choose a provider. Enable and configure
every provider you intend to offer, but do not set a global
`PLATFORM_STORAGE_TYPE` selector.

For S3-compatible storage, set
`PLATFORM_STORAGE_S3_ENABLED=true` and configure the exact adapter properties
through these Fly secrets/environment variables:

- `PLATFORM_STORAGE_S3_ENDPOINT` — absolute HTTP(S) endpoint URI
- `PLATFORM_STORAGE_S3_BUCKET` — bucket name
- `PLATFORM_STORAGE_S3_REGION` — region (defaults to `us-east-1`)
- `PLATFORM_STORAGE_S3_ACCESS_KEY` and `PLATFORM_STORAGE_S3_SECRET_KEY` —
  both set together, or both omitted when the Fly machine's default AWS
  credential chain is deliberately configured

The adapter uses path-style addressing. Use TLS for non-local staging and
production endpoints. Scope the credentials to the dedicated UEP bucket and
minimum required object operations; do not reuse a broad account key.

If storage is configured as Google Drive, set these Fly secrets:
`PLATFORM_STORAGE_GOOGLE_DRIVE_ENABLED=true`,
`PLATFORM_STORAGE_GOOGLE_DRIVE_CLIENT_ID`,
`PLATFORM_STORAGE_GOOGLE_DRIVE_CLIENT_SECRET`,
`PLATFORM_STORAGE_GOOGLE_DRIVE_REFRESH_TOKEN`, and
`PLATFORM_STORAGE_GOOGLE_DRIVE_FOLDER_ID` (recommended for explicit
destination isolation). The backend refreshes access tokens from the OAuth
refresh token and caches them only until shortly before expiry. The static
`PLATFORM_STORAGE_GOOGLE_DRIVE_ACCESS_TOKEN` setting is development-only;
production requires the complete refresh credential set. The OAuth grant used by this adapter must request the exact scope
`https://www.googleapis.com/auth/drive`. This is the supported scope for the
current manual-folder-ID integration: it permits metadata lookup plus file
creation, download, and deletion for the OAuth principal. Do not use
`drive.metadata.readonly` (read-only; it cannot perform content operations)
or assume `drive.file` can access an arbitrary manually configured folder.
`drive.file` is limited to files created/opened by the app or explicitly
shared with it through a file-picker flow; this adapter does not implement
that picker/bootstrap flow.

The `drive` scope is a **restricted, broad-access scope**: it grants view and
manage access to all Drive files available to the OAuth principal. Use a
dedicated Google Workspace storage user, not a personal account; give it
Content manager access only to the UEP storage Shared Drive, do not add it to
other Shared Drives, and keep unrelated files out of its My Drive and
"Shared with me". Mint the refresh token only after granting the exact scope.
If the OAuth consent grant changes, obtain a new refresh token with the new
scope.

For a Workspace-only staging deployment, use an OAuth consent-screen
**Internal** user type and keep the storage principal in that Workspace
organization. Do not rely on an External app left in **Testing** for a
long-lived staging credential: Google issues refresh tokens for that setup
with a seven-day lifetime when Drive scopes are requested. For public
production, publish the External app and complete restricted-scope OAuth
verification; applications that store or transmit restricted-scope data on
servers must also complete the applicable security assessment. If these
requirements or the broad Drive access model are unacceptable, use the
S3-compatible provider rather than weakening the Drive authorization checks.

When generating the refresh token, use an authorization-code flow with
`access_type=offline` so Google issues a refresh token. If a new refresh
token is not returned after a prior grant, repeat consent with
`prompt=consent` after verifying the account and exact scope. Store the
result only as the Fly secret; never commit it, print it, or paste it into
chat.

For `GOOGLE_SHARED_DRIVE`, also set
`PLATFORM_STORAGE_GOOGLE_DRIVE_SHARED_DRIVE_ID` and configure the folder ID
to a folder inside that shared drive. The adapter refuses to start in Shared
Drive mode when no destination folder is set. Before every upload, it now
queries Drive for the configured folder's ID, folder MIME type, and
`driveId`, and fails closed unless the folder belongs to the configured
Shared Drive. This prevents a My Drive folder or a folder from another Shared
Drive from silently receiving tenant content. Verify the configured folder
and permissions with a staging upload/download/delete test before enabling
tenant bindings.

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
different username. Fly Machines have outbound IPv6 connectivity, and Supabase
recommends the direct PostgreSQL endpoint for persistent backends such as
Fly.io VMs. For a fresh UEP staging project, use the direct connection string
shown by the Supabase Dashboard's **Connect** panel:

```text
SUPABASE_DB_URL=jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres?sslmode=require
SUPABASE_DB_USER=postgres
SUPABASE_DB_PASS=<database-password>
```

Use the actual project reference and password; never commit or print them.
The direct endpoint is IPv6-only on the Free tier unless an IPv4 add-on is
enabled. If a runtime connectivity test proves that the Fly machine cannot
reach the direct IPv6 endpoint, switch to the **Shared Pooler — Session**
connection mode, copying its exact host from the Connect panel and using the
username `postgres.<project-ref>`:

```text
SUPABASE_DB_URL=jdbc:postgresql://<exact-session-pooler-host>:5432/postgres?sslmode=require
SUPABASE_DB_USER=postgres.<project-ref>
```

Do not use Shared Pooler transaction mode (port 6543) for this Spring Data JPA
and Flyway service: transaction pooling does not preserve all session features
and does not support prepared statements. Session mode (port 5432) supports
them. The production startup guard now rejects a missing/non-PostgreSQL JDBC
URL, a missing database username, a missing or insecure SSL mode, or non-HTTPS
OIDC issuer/JWKS URLs when the Docker image's `production` profile is active.
Use `sslmode=require` as the minimum encrypted transport setting; after
installing the Supabase CA certificate into the Fly image as a protected secret
file, prefer `sslmode=verify-full` for certificate and hostname verification.

Before the first deployment, verify the connection identity with read-only
queries:

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
Pages origin. The production backend startup guard requires the Fly runtime
to set `LIVEKIT_URL` to a public `wss://` endpoint and `LIVEKIT_API_URL`
to a public `https://` endpoint; leaving the development localhost defaults
will make the production process fail closed.

## Staging identity and data qualification

The staging account matrix is supplied to GitHub Actions as an environment
secret named `UEP_E2E_TEST_ACCOUNTS_JSON`. The product workflow remains disabled
until the identity and database safety gates have been configured. It must be a JSON object with these
keys:

`organization_owner`, `organization_administrator`,
`branch_administrator`, `academic_administrator`, `exam_controller`,
`finance_administrator`, `teacher`, `evaluator`,
`teaching_assistant`, `mentor`, `student`, `guardian`,
`support_operator`, and `auditor`.

Each value must contain `username`, `password`, `roleName`,
`displayName`, and `tenantId`.
An optional `UEP_E2E_SECOND_TENANT_TEACHER_ACCOUNT_JSON` secret contains one
additional account object with the same fields; its `tenantId` must equal
`UEP_STAGING_SECOND_TENANT_ID` and differ from the primary tenant. When
configured, the product test verifies that this second-tenant teacher receives
HTTP 404 when requesting submissions for an existing primary-tenant assignment.
Without it, the cross-tenant live assertion is explicitly skipped and remains a
release-qualification gap. Store real test credentials only in the
GitHub `staging` Environment secret. First run with
`UEP_STAGING_SEED_DRY_RUN=true`; review the database/tenant identity and
planned record list before switching it to `false`. Also configure the
`UEP_STAGING_SEED_CONFIRMATION` Environment secret to the exact value
`UEP-STAGING-SEED-CONFIRMED`; the seeder compares this setting with a fixed
literal in the checked-in test source. **This value is public and is not a
security secret or an independent authorization factor.** It is only a
configuration-presence sentinel that causes the seeder to abort when missing
or incorrect. Do not rely on it to protect staging credentials or authorize
writes; the main-only GitHub Environment deployment policy and required
reviewers are the server-enforced trust boundary, while the per-run workflow
input, repository dry-run variable, owner authorization, tenant/host allowlists,
and database identity check are additional operational gates. Use non-customer
accounts and test data.

Set these GitHub repository variables:

| Variable | Example |
|---|---|
| `UEP_STAGING_E2E_ENABLED` | `true` only after the environment is ready |
| `UEP_E2E_BASE_URL` | `https://uep-frontend-testing.pages.dev` |
| `UEP_E2E_BACKEND_URL` | `https://uep-backend-testing.fly.dev` |
| `UEP_E2E_KEYCLOAK_ISSUER` | Exact `https://.../realms/education` issuer |
| `UEP_STAGING_PRODUCT_E2E_ENABLED` | `true` only after the database identity endpoint and test accounts are ready |
| `UEP_STAGING_SEED_TENANT_ID` | Exact expected tenant UUID for the primary staging dataset |
| `UEP_STAGING_SEED_DRY_RUN` | Keep `true` for read-only preflight; set to `false` only as an additional repository-side write gate. Each write run also requires dispatch input `authorize_synthetic_writes=yes` (default `no`) |
| `UEP_STAGING_ALLOWED_FRONTEND_HOST` | Exact hostname of the isolated staging Cloudflare Pages frontend; no scheme or path |
| `UEP_STAGING_ALLOWED_BACKEND_HOST` | Exact hostname of the isolated staging Fly API; no scheme or path |
| `UEP_STAGING_SECOND_TENANT_ID` | Separate staging tenant UUID used for a real cross-tenant access-denial assertion |

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

Seed only through the guarded, idempotent staging seeder in
`apps/web/test/staging/product-workflows.staging.spec.ts`. Before its first
write, it requires all of the following: the exact staging frontend/backend
hostnames from the GitHub Environment allowlist, the expected tenant UUID, the
confirmation configuration sentinel (not a security secret), an authenticated
organization-owner identity, and a successful call to
`GET /api/v1/platform/staging-database-identity`. That backend endpoint is
available only when `PLATFORM_STAGING_DATABASE_IDENTITY_ENABLED=true`, requires
`OPERATIONS_MANAGE`, reads the actual JDBC connection metadata, and fails
closed unless its host and database name match the independently configured
`PLATFORM_STAGING_EXPECTED_DB_HOST` and
`PLATFORM_STAGING_EXPECTED_DB_NAME`. The seeder uses authenticated domain APIs,
stable tenant-derived codes, create-if-absent behavior, and never deletes
records. It seeds the minimum coherent branch/academic/curriculum/batch/
enrollment/teacher-assignment data, then exercises assignment submission and
grading, scheduled-class manual attendance, batch-targeted announcement delivery
and acknowledgement, question-bank redaction plus assessment attempt/autosave/
submission, finance-administrator invoice creation/issuance, and a guardian
cross-learner gradebook denial assertion. The guardian assertion proves only
that access fails closed: the backend currently has no persisted, verified
Guardian-to-learner relationship model, so positive guardian data access remains
an explicit product blocker. The finance fixture creates no payment and never
invokes settlement or capture APIs. Do not run blind SQL inserts to bypass domain
invariants.

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
