# UEP — OCI + Cloudflare Pages + R2 staging deployment

**Status: target architecture prepared; no provider deployment has been performed.**  
**Last reviewed: 2026-11-02**

This runbook supersedes the historical Fly.io + Supabase topology for the next
staging deployment. It is deliberately written as a gated procedure: do not
create resources, enter payment details, upgrade a plan, or enable a metered
service merely to progress through a step.

## 1. Target architecture

    Browser
      |
      v
    Cloudflare Pages
      uep-frontend-testing.pages.dev
      React/Vite static frontend
      |
      | HTTPS API calls + Keycloak Authorization Code / PKCE S256
      v
    OCI Always Free A1 VM (ARM64; one initial host)
      |
      +-- HTTPS reverse proxy
      |     /api/*       -> UEP Spring Boot API
      |     /actuator/health -> API health only
      |     /realms/*, /resources/*, /admin/* -> Keycloak
      |
      +-- UEP Spring Boot API + scheduled workers
      |
      +-- Keycloak 26.7.x
      |
      +-- PostgreSQL 18.4
            public schema -> UEP application
            keycloak schema -> Keycloak only

    UEP API -- S3-compatible HTTPS --> Cloudflare R2 (private bucket)

The frontend stays on Cloudflare Pages. OCI hosts the API, identity provider,
and PostgreSQL so the first staging stack does not depend on Fly.io or the
unqualified Supabase project. R2 is the intended durable object store; its
activation is a separate cost gate (see section 8).

This is a single-host staging topology, not a highly available production
design. The VM and its block volume are single points of failure. OCI Always
Free capacity is regional and can be unavailable; low-utilization A1 instances
may be reclaimed. Do not promise an uptime SLA from this design.

## 2. Hard cost and safety gates

- OCI must remain on Free Tier. Create only resources explicitly marked
  **Always Free eligible** in the tenancy's home region. Never upgrade to
  Pay As You Go, use trial credits as a substitute for Always Free, or
  provision a resource whose cost is unclear.
- If the OCI signup path asks for a payment card and the account cannot use
  an eligible institution/Oracle Academy route, stop. Do not borrow a card,
  use someone else's payment method, or accept an authorization hold.
- Cloudflare Pages remains on the Free plan. Keep production/preview Git
  deployments disabled and publish only manually approved commits.
- R2 is usage-metered beyond its monthly free allowance. Its documented free
  tier is 10 GB-month Standard storage, 1 million Class A operations and
  10 million Class B operations; Internet egress is free. Those allowances
  are not hard spending caps. The connected Cloudflare account currently
  returns API error 10042 ("Please enable R2 through the Cloudflare
  Dashboard"). R2 has not been enabled and no bucket or R2 key exists.
- Do not enable R2 until the owner has reviewed its activation/billing screen
  and UEP has application-level total-storage and request-rate guardrails.
  A per-file upload limit alone cannot enforce the account-wide R2 free tier.
  Do not assume an alert prevents overage charges.
- Do not activate Workers Paid, Stream, Images, Argo, or other metered
  Cloudflare products for this deployment.

## 3. Known provider and repository state

Verified during the 2026-11-02 audit:

- Cloudflare account is connected. The **uep-frontend-testing** project exists,
  is linked to **shreyashjagtap157/UEP** / **main**, uses **apps/web**, builds
  to **dist**, and has Git production/preview deployments disabled.
- The current testing Pages configuration still points
  **VITE_BACKEND_URL** at the old Fly hostname and is missing a verified
  **VITE_KEYCLOAK_URL**. Its latest deployed bundle is stale and must not be
  treated as a qualified staging release.
- **uep-frontend-demo** is a separate local/demo project. Do not alter it or
  use it as evidence for the real product.
- The account has no Cloudflare DNS zones attached. A custom domain has not
  been verified for this deployment.
- R2 is not enabled (API error 10042); no bucket or credentials have been
  created.
- No OCI tenancy or VM is connected/available to this session. OCI account
  signup, home region, A1 capacity, and free eligibility remain unverified.
- Existing Fly.io configurations are not approved for use: they specify
  always-on machines and their live account state/cost were not requalified.
- The previously connected Supabase project is not an approved UEP staging
  database. Do not initialize it or run the UEP migrations against it.
- Current UEP CI exercises PostgreSQL 17.11 and 18.4. The repository contains
  a PostgreSQL 17 UUIDv7 compatibility callback. Prefer PostgreSQL 18.4 on
  the new self-hosted deployment to match one CI matrix target.

## 4. OCI VM and network qualification

**Do not provision until a Free Tier-only tenancy is confirmed.**

Target the home-region A1 allocation only:

- 2 OCPUs and 12 GB RAM total.
- Keep the combined boot and block-volume allocation within the current
  Always Free allowance.
- Attach a persistent block volume for PostgreSQL data and upload staging.
  Do not put database data or resumable-upload chunks on the container
  writable layer.
- Size the host as one small staging node. Keep memory headroom for Linux,
  Docker, PostgreSQL, Keycloak, the API, and reverse proxy. Do not deploy
  LiveKit/Egress on the initial node.
- Pin container image tags. Verify ARM64 manifests and actually build/start
  the API and Keycloak images for **linux/arm64** before creating the VM
  workload. The existing Dockerfiles were previously built in x86 CI; ARM64
  compatibility is not yet qualified.
- Expose only HTTPS (443) publicly once the TLS endpoint is ready. Expose
  HTTP (80) only if required for certificate validation/redirects. Do not
  publish PostgreSQL (5432), Keycloak management/health (9000), or the
  Spring management port (8080) directly to the Internet.
- Restrict SSH to the administrator's current source IP or use an approved
  secure access path. Do not open SSH to all IPv4 addresses.
- Keep PostgreSQL on a private container network with no public port mapping.

### Stable HTTPS hostname is a blocker

Keycloak's issuer is a security trust anchor; HTTP or an unverified temporary
hostname is not acceptable. Use an owned domain with a verified TLS
certificate, or separately qualify a stable public-IP certificate and
automatic renewal before deployment. Let's Encrypt IP certificates use its
short-lived certificate profile; renewal must be fully automated and
observed. Do not configure the frontend or API with a guessed issuer URL.

## 5. PostgreSQL ownership and migrations

Use one PostgreSQL 18.4 instance on the persistent OCI volume. Keep the
database private and create distinct identities:

- **uep_migrator**: owns the UEP **education_platform** database/schema objects
  and is used only by the explicit migration runner.
- **uep_runtime**: standalone login used by Spring Boot. It must not be a
  superuser, database/schema creator, role creator, replication role,
  BYPASSRLS role, member of another role, or owner of application objects.
- **uep_keycloak**: standalone Keycloak login; owns only the **keycloak**
  schema and is not the UEP application runtime identity.

Create database roles and the database through a local administrative
PostgreSQL session. Set passwords interactively or through a secure secret
manager; never put literal passwords in SQL files, Compose files, Git, or CI
logs. Create the **education_platform** database owned by **uep_migrator**,
then create the dedicated **keycloak** schema owned by **uep_keycloak**.
Grant explicit database CONNECT to the two runtime roles. Do not give
**uep_runtime** schema CREATE or object ownership. Do not grant broad role
membership or BYPASSRLS.

The repository's **DatabaseMigrationRunner** validates the runtime role,
applies/validates Flyway migrations with the migration identity, and grants
the limited application privileges. Use these environment variables for the
one-shot migration process only:

- **UEP_DATABASE_MIGRATION_JDBC_URL** — direct private JDBC URL.
- **UEP_DATABASE_MIGRATION_USERNAME** — **uep_migrator**.
- **UEP_DATABASE_MIGRATION_PASSWORD** — migration secret.
- **UEP_DATABASE_RUNTIME_USERNAME** — **uep_runtime**.

After migration succeeds, start the API with the runtime identity only. The
new **application-oci.yaml** profile sets **spring.flyway.enabled=false**;
activate it alongside production using
**SPRING_PROFILES_ACTIVE=production,oci**. Never start the request-serving API
as the migration/schema-owner role and never rely on application startup to
apply DDL.

Keycloak uses the same private PostgreSQL server but connects as
**uep_keycloak** with **KC_DB_SCHEMA=keycloak**. Its migrations must not use
**uep_migrator** or **uep_runtime**.

**Tenant isolation boundary:** UEP's reviewed application currently enforces
tenant boundaries in its service/repository layer; it does not have a
complete transaction-scoped tenant-GUC plus policy set for PostgreSQL RLS.
Do not add an ensure_rls trigger that merely enables RLS without matching
tenant-aware policies. Do not solve permission errors by using **postgres**,
a superuser, or a BYPASSRLS application role. Qualify cross-tenant negative
API tests before staging acceptance.

## 6. OCI runtime configuration

Run the API with both profiles active:

    SPRING_PROFILES_ACTIVE=production,oci
    PLATFORM_DEV_MODE=false
    PLATFORM_LIVEKIT_ENABLED=false
    SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/education_platform
    SPRING_DATASOURCE_USERNAME=uep_runtime
    SPRING_DATASOURCE_PASSWORD=<secret>
    OIDC_ISSUER_URI=https://<PUBLIC_HOST>/realms/education
    OIDC_JWK_SET_URI=https://<PUBLIC_HOST>/realms/education/protocol/openid-connect/certs
    PLATFORM_RECORDING_PLAYBACK_SECRET=<random-secret-at-least-32-characters>
    PLATFORM_INTEGRATION_SECRET_KEY=<random-secret-at-least-32-characters>

Use a private Docker network for the database URL above. Supply actual secrets
through a protected environment/secret store; do not commit a .env file.
The API profile keeps Flyway off for the runtime process. Keep the single
API instance's scheduled workers enabled only while exactly one instance is
running; multiple API replicas would otherwise need a distributed worker
lease/leader mechanism to avoid duplicate pollers.

The chunked upload handler stages temporary files on local disk. The API
container runs as non-root UID/GID **10001:10001**. Mount its staging root to
the persistent OCI volume and set **PLATFORM_STORAGE_STAGING_ROOT**; provision
the host directory with ownership/permissions that allow UID/GID 10001 to
read and write it. Do not run the API as root to work around a volume
permission error. Keep the staging volume permission-restricted, monitor free
space, and configure **PLATFORM_MAX_UPLOAD_BYTES** to a conservative value
that does not exceed the provider quota. Keep LiveKit/Egress disabled until
separately provisioned and tested.

## 7. Keycloak and HTTPS routing

Build the optimized image from **infra/keycloak/Dockerfile** and import
**infra/keycloak/education-realm-staging.json**. Do not run the local
development **start-dev** command in staging.

Configure Keycloak with:

- **KC_DB=postgres**
- **KC_DB_SCHEMA=keycloak**
- **KC_DB_URL=jdbc:postgresql://postgres:5432/education_platform**
- **KC_DB_USERNAME=uep_keycloak**
- **KC_DB_PASSWORD=<secret>**
- **KC_HOSTNAME=https://<PUBLIC_HOST>**
- **KC_HOSTNAME_STRICT=true**
- **KC_HOSTNAME_STRICT_HTTPS=true**
- **KC_HTTP_ENABLED=true** behind the HTTPS reverse proxy
- **KC_HTTP_PORT=8080**
- **KC_HTTP_MANAGEMENT_PORT=9000**
- **KC_PROXY_HEADERS=xforwarded**

Keep the Keycloak management port private. Configure the reverse proxy so
**/api/** and only the intended health route reach Spring Boot, while
**/realms/**, **/resources/**, and administrator paths reach Keycloak.
Do not expose actuator metrics, Prometheus, Modulith, or Keycloak health
endpoints publicly.

The staging realm already has the required Pages client:
**platform-web**, Authorization Code flow, PKCE S256, no implicit flow, and
no direct access grants. Its redirect URI and web origin currently permit
**https://uep-frontend-testing.pages.dev**. Keep that exact origin unless the
frontend hostname changes.

Before building frontend assets, verify all three endpoints from outside
the VM:

1. **https://<PUBLIC_HOST>/realms/education/.well-known/openid-configuration**
2. The discovery document's issuer exactly equals
   **https://<PUBLIC_HOST>/realms/education**.
3. The published JWKS endpoint returns valid keys at
   **https://<PUBLIC_HOST>/realms/education/protocol/openid-connect/certs**.

Also verify **https://<PUBLIC_HOST>/actuator/health** through the restricted
reverse-proxy route. Never build the Vite bundle using a guessed issuer.

## 8. R2 object storage

The existing **S3StorageAdapter** supports a custom endpoint, path-style
addressing, static credentials, and a configurable region. R2 can use that
adapter without a second storage implementation once the bucket is enabled.

Use a private bucket and a bucket-scoped R2 S3 token. The backend values are:

- **PLATFORM_STORAGE_S3_ENABLED=true**
- **PLATFORM_STORAGE_S3_ENDPOINT=https://<ACCOUNT_ID>.r2.cloudflarestorage.com**
- **PLATFORM_STORAGE_S3_REGION=auto**
- **PLATFORM_STORAGE_S3_BUCKET=<PRIVATE_BUCKET>**
- **PLATFORM_STORAGE_S3_ACCESS_KEY=<secret>**
- **PLATFORM_STORAGE_S3_SECRET_KEY=<secret>**

Keep all R2 credentials on the server. Never put them in Pages variables,
Vite build-time variables, browser JavaScript, or source control. Do not make
the bucket public or enable r2.dev public access. UEP downloads must
continue through the authenticated backend and tenant authorization path.
An active tenant administrator must configure the tenant storage binding as
**S3_COMPATIBLE** before that tenant can upload.

**Do not enable R2 yet.** The safeguards now exist in source but have not
been qualified on the exact current `main` head or applied to a qualified
staging database:

- Migration **V047** adds a persistent provider-wide byte ledger, per-tenant
  byte accounting, and provider/tenant operation counters. The seeded
  `S3_COMPATIBLE` limits are 8,000,000,000 total bytes, 1,000,000,000 bytes
  per object, 25,000 Class A operations/day, 750,000/month, 250,000 Class B
  operations/day, 7,500,000/month, plus tenant-level daily operation limits.
- **V048** allows a released reservation key to be reused while preserving
  historical release rows.
- The `S3_COMPATIBLE` provider is seeded with reads and writes **disabled**.
  The application reserves up to three provider attempts per operation,
  matching the configured S3 client retry ceiling. Tenant and provider
  budgets are enforced by the backend; they are not a Cloudflare billing
  hard cap and do not account for R2 usage generated outside UEP.

Before enabling R2, require a complete green CI run for the exact current
`main` SHA, validate V047/V048 on a fresh disposable PostgreSQL database,
and test reservation/commit/release/retry, concurrency, deletion and retention,
operation-budget exhaustion, rollback compensation, and cross-tenant
isolation. Only then apply migrations through the protected migration
workflow to the approved staging database and re-run provider tests against
the real R2 bucket. The owner must separately verify R2 activation, existing
account-wide usage, and the billing screen. If no-charge activation and
remaining free-tier headroom cannot be established, leave provider reads and
writes disabled and keep external uploads disabled rather than risking an
invoice.

## 9. Cloudflare Pages staging frontend

Keep **uep-frontend-testing** as the product staging project and
**uep-frontend-demo** as a separate local demo. Do not enable Git
auto-deployments. Configure build-time variables only after the OCI API and
Keycloak endpoints have been verified:

    VITE_LOCAL_MODE=false
    VITE_BACKEND_URL=https://<PUBLIC_HOST>
    VITE_KEYCLOAK_URL=https://<PUBLIC_HOST>
    VITE_KEYCLOAK_REALM=education
    VITE_KEYCLOAK_CLIENT_ID=platform-web

Use the existing project settings:

- Repository: **shreyashjagtap157/UEP**
- Production branch: **main**
- Root directory: **apps/web**
- Build command: **npm ci --ignore-scripts && npm run build**
- Output directory: **dist**

Build from the exact audited **main** SHA and manually publish the build.
Do not assume runtime Pages variables can change Vite's already-built
JavaScript. Verify the emitted bundle contains the correct OCI API and
Keycloak URLs and has **VITE_LOCAL_MODE=false**. Then test sign-in, token
issuance, CORS preflight, **/api/v1/me**, and tenant-scoped resource access.

## 10. Staging workflow trust boundary

The real staging E2E workflow is privileged: it can read real test-account
credentials and authenticate against the deployed application. Treat it as a
secrets-bearing deployment workflow, not as an ordinary PR test.

Before enabling `UEP_STAGING_E2E_ENABLED=true` or adding any staging identity
secret, configure the repository's GitHub **staging** Environment independently:

- Under **Settings → Environments → staging → Deployment branches and tags**,
  select only the `main` branch. Do not allow all branches, patterns that
  include contributor branches, tags, or PR merge refs.
- Configure at least one required reviewer who is authorized to approve
  staging access. Disable administrator bypass where the repository policy
  permits it.
- Keep the endpoint variables and the real 14-role account matrix scoped to
  this Environment. Never place them in repository-wide variables/secrets.
- Protect `main` against unreviewed workflow changes and require the CI checks
  before changes to the workflow can land.

The workflow is deliberately `workflow_dispatch`-only, checks the selected
ref is `refs/heads/main`, checks out `main`, and scopes Environment secrets to
the staging-test steps. Those workflow checks are defense in depth, **not** an
independent security boundary: GitHub loads a manually dispatched workflow
definition from the selected ref. The Environment's server-side main-only
deployment-branch rule is mandatory and must be verified in repository
settings before staging can be enabled.

The workflow disables Playwright traces, screenshots, and video; the retained
HTML/JUnit artifact is limited to seven days. Keep test assertions and test
logs free of credentials and tenant identifiers.

The database migration workflow is separately privileged. The
`vercel-database-migrations` GitHub Environment must also restrict deployment
branches to `main` only and require an authorized reviewer before it receives
the schema-owner migration credentials. Its workflow checks the selected ref,
checks out `main` explicitly, and maps migration credentials only to the
credential-check and migration steps. These checks are defense in depth; the
Environment's server-side branch restriction is the trust boundary. Do not
reuse the staging test-account Environment for database-owner credentials.

## 11. Release acceptance gates

A real staging deployment is accepted only when all are true:

- OCI tenancy is confirmed Free Tier-only and every resource is marked
  Always Free eligible. No paid upgrade or paid add-on was used.
- A1 ARM64 API and Keycloak images build and start successfully.
- PostgreSQL data is on the persistent volume; port 5432 is not public.
- Migration is executed once by **uep_migrator**; Flyway history reaches the
  current migration ceiling; API starts as **uep_runtime**.
- Keycloak uses only the **keycloak** schema and **uep_keycloak** login.
- The public HTTPS host, issuer, discovery document, and JWKS agree exactly.
- API health, OIDC login, CORS, and **/api/v1/me** succeed from Pages.
- The Pages bundle is built from the audited commit and real (non-demo)
  environment values; automatic deployments remain disabled.
- R2 is enabled only after free-tier activation and hard application storage
  and request limits are qualified. Upload, retrieval, deletion, tenant
  isolation, and restart persistence all pass.
- The GitHub `staging` Environment independently restricts deployments to `main` and requires an authorized reviewer.\n- The 14-role staging identity matrix and cross-role workflows pass, including
  cross-tenant negative tests. Staging E2E remains disabled until test
  accounts and all endpoint variables exist.
- Backup/restore is tested against a disposable database identity; no restore
  drill may target a production or unknown database.

## 12. Explicit blockers — do not bypass

1. **OCI account/signup:** no connected OCI tenancy is available. Determine
   whether the user can access OCI through an eligible card-free route such
   as Oracle Academy; otherwise do not ask for or use payment details.
2. **A1 capacity:** home region and capacity must be inspected in the user's
   OCI dashboard before any allocation.
3. **Stable HTTPS hostname:** no Cloudflare DNS zone or custom domain has
   been verified. Qualify domain or IP-certificate renewal before configuring
   OIDC.
4. **ARM64 image qualification:** Dockerfiles have not yet been built/tested
   on ARM64 by CI.
5. **R2 no-charge protection:** R2 is not enabled; hard account-wide quota and
   operation-rate safeguards must be implemented before activation.
6. **GitHub staging Environment:** its server-side main-only deployment-branch rule and required-reviewer policy have not been independently verified in repository settings.\n7. **Staging test identities:** the real 14-role test account matrix is not
   configured; staging E2E is skipped, not passed.

No deployment, R2 activation, database migration, secret creation, or provider
billing change is authorized by this runbook.
