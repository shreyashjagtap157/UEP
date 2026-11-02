# Retired hosted staging target — decommission record

**STATUS: RETIRED / NOT DEPLOYABLE.** This file is an audit record, not a runbook. Do not provision, start, deploy, migrate, move or resize any Fly, Supabase, OCI or Vercel resource using historical values. The supported replacement is [LOCAL_NATIVE_WINDOWS.md](LOCAL_NATIVE_WINDOWS.md).

## Verified hosted state (2 November 2026)

### Fly.io
- Owner-reported Personal Organization app: uep-backend-testing.
- Machine: d89565db307d58, Sydney (syd), shared-cpu-2x / 2048 MB.
- Machine status: stopped after exhausting the maximum 10 restart attempts.
- No backend volume was attached.
- Last known machine image: release v7.
- The Keycloak app uep-keycloak-testing does not exist.
- The owner-provided machine logs show Spring Boot failing during context initialization before binding port 8080. Current CI has since qualified the current source's production-profile JAR startup, but no current image has been deployed to Fly.

No Fly control-plane credential or management connector was available in this session. No Fly machine was started, replaced, moved, resized or destroyed. The stopped machine and app record still exist and require the owner to use the Fly dashboard to destroy them after checking the resource and billing inventory. Do not claim permanent deletion.

### Supabase
- Connected UEP project ref: rykmwcslbzmkzmnnzvxm.
- Region: ap-southeast-1; plan: Free; PostgreSQL 17.11.
- Before pause, the public and keycloak schemas had no UEP tables and no Flyway history.
- No UEP migrations were applied.
- The Supabase pause operation returned success; a later project inspection reported INACTIVE.

The project is paused, not permanently deleted. The owner may delete it from the Supabase dashboard after checking for data or resources that should be retained. No project deletion is claimed.

### Cloudflare Pages
The two static Pages projects are independent of the API/database retirement. The last inspected uep-frontend-testing bundle was stale and referenced retired service configuration; it is not a qualified connected product. uep-frontend-demo is an offline fixture-based presentation frontend and was not modified. Cloudflare Pages does not host the UEP Spring API, Keycloak or PostgreSQL.

## Repository retirement

Removed from main:
- Fly API deployment manifest apps/platform-server/fly.toml.
- Fly Keycloak deployment manifest infra/keycloak/fly.toml.
- Hosted Supabase database migration workflow .github/workflows/database-migration.yml.
- Remote staging E2E workflow .github/workflows/staging-e2e.yml.
- Vercel multi-service configuration, backend runtime profile and Vercel-specific backend/build configuration.

CI still performs source, unit, integration, database compatibility and build qualification on GitHub-hosted public-repository runners. Those checks build/test artifacts only; they do not deploy backend or database infrastructure. Dockerfiles remain solely for CI container-build/smoke qualification and are not used by the native Windows launcher.

## Supported local replacement

- scripts/Start-UEP.ps1 — Demo, Real and Both modes.
- LOCAL_NATIVE_WINDOWS.md — requirements, local URLs, database ownership and operations.
- LOCAL_NATIVE_WINDOWS_TEST_PROMPT.md — full browser/API/PostgreSQL acceptance prompt.

Real mode uses native PostgreSQL, Keycloak and Spring Boot processes. The real browser uses the Vite /api proxy, Spring Boot connects to PostgreSQL using uep_local_runtime, and schema changes are applied separately through the local DatabaseMigrationRunner as uep_local_migrator. Demo mode is intentionally offline and fixture-backed.

## Completion boundary

Completed:
- Fly backend machine confirmed stopped by the owner; no attached backend volume.
- Keycloak Fly app confirmed absent.
- Supabase project successfully paused and subsequently observed as INACTIVE.
- Fly manifests, hosted migration workflow, remote staging E2E workflow and Vercel backend configuration removed from main.
- No hosted UEP database migrations applied.

Not completed:
- Permanent deletion of Fly machine and app record (owner dashboard action still required).
- Permanent deletion of the paused Supabase project (owner dashboard action still required).
- Changes/deletion of Cloudflare Pages projects.

These boundaries are deliberate. No provider-side deletion is simulated or reported as complete.
