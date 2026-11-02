# UEP — Cloudflare Pages + Fly.io + Supabase staging deployment

**Status: provider access and account-specific cost review pending. No Fly Machines have been started and no UEP migrations have been applied.**  
**Last reviewed: 2026-11-02**

This is the active staging deployment runbook. It separates repository readiness, provider access, database initialization, machine provisioning, and product acceptance. A successful container build is not a deployed/working product.

## 1. Target topology

    Browser
      |
      v
    Cloudflare Pages — uep-frontend-testing.pages.dev
      React/Vite static app (VITE_LOCAL_MODE=false)
      |
      | HTTPS API + OIDC Authorization Code / PKCE S256
      +-------------------------------------------+
      |                                           |
      v                                           v
    Fly.io — uep-backend-testing               Fly.io — uep-keycloak-testing
    Spring Boot API                            Keycloak 26.7.x
    Singapore region (sin)                     Singapore region (sin)
      |                                           |
      +---------------------+---------------------+
                            |
                            v
    Supabase project UEP — PostgreSQL, Singapore (ap-southeast-1)
      public schema   -> UEP application, objects owned by uep_migrator
      keycloak schema -> Keycloak only, owned by uep_keycloak

    Optional after qualification:
    UEP API -> Supabase Storage S3 endpoint (private bucket; server-side key only)

The browser talks to Cloudflare Pages and the Fly-hosted API/Keycloak only. It must never receive database credentials or Supabase S3 secret keys. Supabase Auth is not the UEP identity provider: Keycloak is the OIDC issuer, and the UEP Spring API is the sole application database client.

## 2. Verified provider state and release boundary

Observed during the 2026-11-02 audit:

- Cloudflare account is connected. The existing Pages project uep-frontend-testing is GitHub-linked to shreyashjagtap157/UEP / main, uses root apps/web, build command npm ci --ignore-scripts && npm run build, output dist, and Node 22.22.0. Production/preview Git deployment switches are disabled.
- That Pages project currently points VITE_BACKEND_URL at https://uep-backend-testing.fly.dev, has VITE_LOCAL_MODE=false, client ID platform-web, realm education, but lacks VITE_KEYCLOAK_URL. Its latest ad-hoc deployment was built from a dirty, stale commit and is not accepted.
- The separate uep-frontend-demo Pages project is local/demo mode. Do not change it or treat it as a deployed product.
- Supabase organization is on the Free plan. Existing project UEP, ref rykmwcslbzmkzmnnzvxm, region ap-southeast-1, is active/healthy. Its public schema has no UEP application tables and no Flyway history; no UEP migrations have been run. The project is suitable only for a controlled staging trial until its backup/availability and quota limits are acceptable.
- Fly account sign-in and live app/machine/volume inventory were not yet available at the time of this review. The repository contains historical app names uep-backend-testing and uep-keycloak-testing, but their live existence, machine state, regions, volumes, payment method, and cost must be inspected before reuse. Do not assume the old Sydney (syd) settings remain valid.
- GitHub Actions run #939 passed all six CI jobs on source commit e6acf6d4d80c057b5f601fcb4c637ee586b15847; run #941 passed all six jobs on the latest documentation-only main commit. These are repository checks, not provider deployments.
- Existing CORS configuration already allows https://uep-frontend-testing.pages.dev. Do not widen CORS to arbitrary origins.

## 3. Cost, quota, and approval gates

### Fly.io

Fly Machines are billed by the second while running; volumes are charged on provisioned capacity and network egress is separate. The Fly pricing page lists a shared-CPU 2x/2 GB example at approximately US$11.83/month in one region table, but regional markups apply and this is not a Singapore quote. Volumes are listed at US$0.15/GB-month; Asia-Pacific egress is US$0.04/GB; a dedicated IPv4 is US$2/month, while shared IPv4 and Anycast IPv6 are included. See [Fly resource pricing](https://fly.io/docs/about/pricing/) and [the 2026 pricing update](https://fly.io/pricing-update/).

The initial design has two always-on machines (API and Keycloak). Background workers and OIDC sign-in make scale-to-zero undesirable for this stage. Before creating or starting either machine:

1. Sign in to Fly.io and inspect the organization, billing plan, current apps, current machines, regions, volumes, and existing usage.
2. Reuse existing apps only after confirming their ownership and contents; do not delete/stop/move an unknown machine or volume.
3. Obtain the dashboard's region-sin quote for the chosen CPU/RAM sizes and any volume. Include snapshots/egress/IP charges and any required payment method.
4. Present that actual quote and obtain explicit approval for the recurring spend. If Fly requires a new card/payment method or a plan upgrade, stop and let the owner decide in the Fly dashboard. Never ask for card details in chat.

No dedicated IPv4, static egress IP, managed Postgres, paid Fly add-on, or reservation block is required for the initial deployment.

### Supabase

The connected organization is on Free. Current Free-plan allowances include 500 MB database size per project, 1 GB Storage, 5 GB egress, and a 50 MB maximum object size. Free projects can pause after a week of low activity and do not have automatic database backups. The project has a 2 GB underlying disk cap with a read-only safeguard at 95%; this is not a 2 GB usable database quota. See [Supabase pricing](https://supabase.com/pricing), [billing quotas](https://supabase.com/docs/guides/platform/billing-on-supabase), [upload limits](https://supabase.com/docs/guides/storage/uploads/file-limits), [database size](https://supabase.com/docs/guides/platform/database-size), and [Free-plan backup policy](https://supabase.com/kb/faqs/platform/free-plan-backups).

Use this project for staging only. Keep uploads at or below 50 MB; do not rely on an automatic backup/restore capability. Schedule and verify manual backups before loading any meaningful test data. Do not upgrade the Supabase plan or enable paid add-ons without a separate owner decision.

### Cloudflare

Reuse the existing Free Pages project. Keep paid Workers, Stream, Images, Argo, and other metered products disabled. Keep preview and automatic Git deployments disabled until a clean, manually qualified main deployment is accepted.

## 4. Supabase database initialization — non-destructive, empty-schema gate

The current project is confirmed to have no UEP application tables/Flyway history in public. Recheck immediately before migration:

- UEP project ref must still be rykmwcslbzmkzmnnzvxm.
- Database must still be the intended staging project and region ap-southeast-1.
- public must not contain application/customer tables from another workload.
- List migrations and inspect the latest status. Do not run DROP, CLEAN, reset, restore, or baseline-on-migrate.

Use the dashboard's Connect panel to copy the exact connection method and endpoint. For a persistent Fly VM, prefer a direct IPv6 PostgreSQL connection if Fly connectivity supports it. If the Fly VM is IPv4-only, use Supabase's shared pooler — session mode, port 5432. Do not use transaction mode (usually port 6543) for the long-lived Hikari/JDBC application or Keycloak connections. The exact pooler host and username suffix are cluster/project specific and must be copied from the dashboard, never guessed. See [Supabase connection methods](https://supabase.com/docs/guides/database/connecting-to-postgres).

Create distinct database identities. Do not run Flyway as the application runtime user, and do not use the Supabase postgres owner for the running API:

| Role | Responsibility | Required boundary |
|---|---|---|
| uep_migrator | One-shot UEP Flyway migrations | Login; may create UEP objects in public; never used by API/Keycloak |
| uep_runtime | Spring Boot API | Standalone least-privilege login; no schema/database CREATE, superuser, role membership, RLS bypass, or object ownership |
| uep_keycloak | Keycloak persistence | Owns only the keycloak schema; no UEP public object ownership |

Create role passwords in a secure dashboard/secret-entry flow, not in committed SQL, GitHub workflow source, application files, or chat. Revoke CREATE on public from PUBLIC if present so the runtime role cannot acquire it indirectly. Ensure the runtime role has no membership in owner roles. Create the keycloak schema owned by uep_keycloak, and grant that role only the database connect and schema privileges it needs. Do not add role memberships that bypass these ownership boundaries.

The one-shot migration is the existing DatabaseMigrationRunner. Its inputs are:

- GitHub Environment secret UEP_DATABASE_MIGRATION_JDBC_URL — JDBC URL for the exact direct/session-mode endpoint, with TLS required.
- GitHub Environment secret UEP_DATABASE_MIGRATION_USERNAME — uep_migrator.
- GitHub Environment secret UEP_DATABASE_MIGRATION_PASSWORD — migration credential.
- GitHub Environment variable UEP_DATABASE_RUNTIME_USERNAME — uep_runtime.

The .github/workflows/database-migration.yml workflow is manual-dispatch-only, requires the main ref and an explicit yes confirmation, checks credentials without printing them, and invokes the runner. Its GitHub Environment must be named supabase-database-migrations, independently allow only main, and require an authorized reviewer. Do not disable this review gate.

The runner validates the runtime role, runs Flyway with baselineOnMigrate=false and cleanDisabled=true, validates migrations, and grants the runtime role CRUD/sequence/type privileges while denying access to the Flyway history table. It also configures default privileges for future migration-created objects.

Before API startup, verify Flyway's current version, success count, table count, constraints/indexes, runtime grants, no runtime schema CREATE, and no access to flyway_schema_history. The API must use SPRING_FLYWAY_ENABLED=false and the runtime role; its Hibernate mode is validate, not schema creation/update.

## 5. Fly.io deployment — API and Keycloak

Target region is sin (Singapore), colocated with the Supabase project. The app config paths are:

- API: apps/platform-server/fly.toml with Dockerfile apps/platform-server/Dockerfile.
- Keycloak: infra/keycloak/fly.toml with Dockerfile infra/keycloak/Dockerfile.

Do not deploy until the actual Fly app inventory and region-specific quote are reviewed. If an app exists in syd, do not just change primary_region and redeploy: plan a parallel Singapore machine, validate it, switch traffic, then retire the old machine/volume only after confirming its contents and cost impact.

### API environment

Public non-secret settings (Fly app configuration):

- SPRING_PROFILES_ACTIVE=production
- SPRING_FLYWAY_ENABLED=false
- PLATFORM_DEV_MODE=false
- PLATFORM_BACKGROUND_WORKERS_ENABLED=true
- PLATFORM_LIVEKIT_ENABLED=false until a real LiveKit endpoint and credentials are qualified
- PLATFORM_UPLOAD_STAGING_ENABLED=false until Supabase Storage S3 and durable Fly staging storage are configured
- PLATFORM_MAX_UPLOAD_BYTES=52428800 (50 MB Free-plan ceiling)
- OIDC_ISSUER_URI=https://uep-keycloak-testing.fly.dev/realms/education
- OIDC_JWK_SET_URI=https://uep-keycloak-testing.fly.dev/realms/education/protocol/openid-connect/certs

Fly secrets (never commit or print):

- SUPABASE_DB_URL — JDBC TLS connection string using the verified direct/session-mode host.
- SUPABASE_DB_USER=uep_runtime
- SUPABASE_DB_PASS — runtime role password.
- PLATFORM_RECORDING_PLAYBACK_SECRET — random strong secret, at least 32 characters.
- PLATFORM_INTEGRATION_SECRET_KEY — random strong secret, at least 32 characters.

Do not enable live learning, SMTP, malware scanning, or external storage adapters without verified endpoints/credentials. If upload support is later enabled, create and attach a persistent Fly volume mounted at /app/var (the Docker image writes resumable staging under /app/var/uploads), set PLATFORM_UPLOAD_STAGING_ENABLED=true, create a private Supabase bucket, enable S3 protocol, and add PLATFORM_STORAGE_S3_ENABLED=true plus the S3 endpoint/region/bucket/access-key/secret-key as Fly secrets/settings. S3 keys have full Storage access and bypass Storage RLS; they must remain server-side only. Supabase S3 does not support object versioning and deletes are permanent. Set the tenant's storage binding only after the API owner account has the required EXTERNAL_STORAGE_MANAGE permission. Do not expose a frontend S3 key or bucket.

### Keycloak environment

Run Keycloak 26.7.x from infra/keycloak/Dockerfile as a separate Fly app in sin. It uses the existing education realm import, public web client platform-web, PKCE S256, and tenant-id protocol mapper. The API's OIDC issuer/JWKS URLs must match its exact HTTPS hostname.

Public settings:

- KC_DB=postgres
- KC_DB_SCHEMA=keycloak
- KC_DB_USERNAME=uep_keycloak
- KC_HOSTNAME=https://uep-keycloak-testing.fly.dev
- KC_HOSTNAME_STRICT=true, KC_HOSTNAME_STRICT_HTTPS=true
- KC_HTTP_ENABLED=true, KC_HTTP_PORT=8080, KC_HTTP_MANAGEMENT_PORT=9000
- KC_PROXY_HEADERS=xforwarded, health and metrics enabled

Fly secrets:

- KC_DB_URL — JDBC TLS URL to the same Supabase database, with default schema keycloak.
- KC_DB_PASSWORD — uep_keycloak credential.
- KC_BOOTSTRAP_ADMIN_USERNAME, KC_BOOTSTRAP_ADMIN_PASSWORD — temporary first Keycloak administrator credentials.

Keep management/health port 9000 private; publish only HTTPS through Fly on the HTTP service's internal port 8080. Verify the issuer discovery document, JWKS endpoint, realm, client redirect/origin and token claims before pointing Pages to it.

## 6. First organization and owner bootstrap

A healthy server is not equivalent to a usable sign-in. The realm import defines platform_super_admin but deliberately contains no user.

1. Sign in to the Keycloak admin console with the temporary bootstrap admin.
2. Create the platform operator user, assign the realm role platform_super_admin, and enable the required credential/passkey/OTP policy.
3. Authenticate as that operator and verify the token includes the required realm role and trusted tenant_id context needed by the API filters.
4. Create the initial tenant through the platform provisioning endpoint.
5. Create/federate the intended organization owner in Keycloak and set the trusted tenant_id user attribute; verify the emitted claim.
6. Call POST /api/v1/identity/bootstrap-owner as the platform operator with the owner's OIDC subject and profile. The flow refuses to create a second initial owner when tenant memberships already exist.
7. Sign in as the owner and verify /api/v1/me, Organization Owner permissions, tenant scoping and branch visibility.
8. Rotate/remove temporary bootstrap access and store operator recovery information securely.

See IDENTITY_BOOTSTRAP.md. Do not put passwords, one-time codes or recovery keys in GitHub issues, chat or migration records.

## 7. Cloudflare Pages deployment

Reuse uep-frontend-testing; do not create a duplicate project. Preserve:

- GitHub repository shreyashjagtap157/UEP, production branch main, path include apps/web/**
- root apps/web, build npm ci --ignore-scripts && npm run build, output dist
- Node 22.22.0

Set both production and preview configuration consistently:

- VITE_LOCAL_MODE=false
- VITE_BACKEND_URL=https://uep-backend-testing.fly.dev
- VITE_KEYCLOAK_URL=https://uep-keycloak-testing.fly.dev
- VITE_KEYCLOAK_CLIENT_ID=platform-web
- VITE_KEYCLOAK_REALM=education

Keep the separate local/demo project unchanged. Keep automatic and preview deployments disabled. Trigger one manually reviewed Pages deployment from a clean main commit after the API and Keycloak endpoints are healthy. Verify the resulting deployment commit matches the selected main SHA and its build config/environment. The current ad-hoc deployment from a dirty historical commit is not accepted.

## 8. Product verification and acceptance

After database migration, API deployment, Keycloak deployment and Pages deployment:

1. Verify Pages returns the built app and the loaded bundle contains VITE_LOCAL_MODE=false and the correct API/issuer URLs.
2. Verify Keycloak discovery/JWKS over HTTPS and exact issuer equality.
3. Verify unauthenticated GET /actuator/health returns healthy; verify protected API calls reject missing/invalid JWTs.
4. Complete platform-operator, tenant, owner bootstrap; verify login, /api/v1/me, tenant and branch isolation.
5. Run the manual staging-e2e workflow with the real 14-role account matrix only after the GitHub staging Environment has main-only branch restrictions, authorized reviewers, required variables/secrets and UEP_STAGING_E2E_ENABLED=true.
6. Run Supabase security/performance advisors, verify the runtime database role cannot CREATE objects or read Flyway metadata, and check no Supabase secret is embedded in the Pages bundle.
7. If storage is enabled, verify private bucket access, per-tenant object prefixes, upload/download/delete, quota release and restart/deploy persistence.
8. Record manual backup/restore evidence; the Free plan has no automatic backup and is not a production DR posture.

**Release remains staging-only** until production-scale load, live identity/provider, backup/restore, observability/alerting, accessibility, external-provider and security acceptance gates are completed.

## 9. Outstanding owner actions / hard stops

- Complete the secure Fly.io and Supabase dashboard sign-in sessions; do not send credentials in chat.
- Inspect Fly organization/app/machine/volume/billing inventory and obtain a current Singapore quote. Explicitly approve recurring machine/volume cost before resources are started.
- In Supabase dashboard, copy the exact direct or session-mode connection details, generate separate DB-role passwords securely, create the roles/schema, and (if uploads are wanted) configure private Storage bucket/S3 credentials.
- Configure the GitHub supabase-database-migrations Environment with main-only branch policy, authorized reviewer and required secrets/variable. Configure the staging Environment likewise.
- Do not change the Pages project to auto-deploy; deploy only a reviewed clean main SHA.
- Do not upgrade Supabase, Fly or Cloudflare plans or enable paid add-ons without a separate owner decision.
