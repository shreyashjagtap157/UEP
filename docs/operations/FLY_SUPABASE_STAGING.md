# Retired hosted staging target — historical status and decommission record

**STATUS: RETIRED / NOT DEPLOYABLE FROM THIS BRANCH.**
**Last verified: 2026-11-02.** Use [LOCAL_NATIVE_WINDOWS.md](LOCAL_NATIVE_WINDOWS.md) for the supported no-container local environment and [LOCAL_NATIVE_WINDOWS_TEST_PROMPT.md](LOCAL_NATIVE_WINDOWS_TEST_PROMPT.md) for integration qualification.

This file is retained as an audit record only. It is not an active deployment runbook. Do not provision, start, move, resize, recreate or migrate any Fly/Supabase resource using historical values in this document.

## Verified hosted state

### Fly.io

The owner inspected the Personal Organization in the Fly dashboard and provided this machine inventory:

- App: uep-backend-testing.
- Machine: d89565db307d58.
- Region: Sydney (syd).
- State: stopped; maximum 10 restart attempts exhausted.
- Resources shown: shared-cpu-2x, 2048 MB.
- Attached backend volumes: none.
- Last known release: v7.
- Keycloak app uep-keycloak-testing: does not exist.

The old machine logs show Spring Boot failed during application-context initialization with a missing Spring Modulith ApplicationModulesRuntime dependency, before port 8080 became available. The current API source was subsequently verified by GitHub CI using the production profile, but no Fly image has been deployed from that verification.

The account is not connected to an authenticated Fly management tool in this session. Therefore:

- No machine was started, resized, moved or destroyed.
- No new Fly app or machine was created.
- The existing Fly machine is still a stopped resource record.
- Deleting the machine and app records themselves remains an owner action in the Fly dashboard. Inspect the stopped machine and then use the dashboard's destroy controls if complete Fly-side removal is required.
- A stopped Fly resource may still have account-specific storage/billing consequences; confirm the final resource list and billing summary after dashboard deletion.

The Fly deployment manifests have been removed from this repository so a normal sync no longer carries the API or Keycloak Fly app configurations.

### Supabase

The only connected Supabase project was:

- Project: UEP.
- Ref: rykmwcslbzmkzmnnzvxm.
- Region: ap-southeast-1.
- Plan: Free.
- PostgreSQL engine: 17.11.

The project was inspected before deactivation. The public and keycloak schemas contained no UEP application tables and no Flyway history. No UEP migration was applied to this hosted project.

On 2026-11-02 the Supabase pause operation succeeded. A subsequent get-project check reported status INACTIVE. This means the hosted project is paused, not deleted. The project can be restored from the Supabase dashboard. Complete deletion, should the owner choose it, must be performed there after checking for unrelated retained data; this session did not delete the project.

The GitHub database-migration workflow has been removed. No database migration action is available from this branch for the hosted project.

### Cloudflare Pages

The Cloudflare Pages frontend projects remain separate from the API/database retirement:

- uep-frontend-testing remains GitHub-linked but its last inspected bundle was stale and referenced the retired Fly services.
- Automatic/preview Git deployments were disabled at the time of inspection.
- uep-frontend-demo is a separate offline/demo project and was not modified.

No API, Keycloak or database service is deployed by Cloudflare Pages. Do not treat the stale testing frontend as a connected product.

## Repository retirement changes

The following deployment entry points have been removed from main:

- apps/platform-server/fly.toml
- infra/keycloak/fly.toml
- .github/workflows/database-migration.yml

The staging browser-test workflow remains a manually enabled test workflow only; it does not provision or deploy backend, Keycloak or database resources. The repository policy verifier rejects the retired deployment artifacts if they are reintroduced.

Dockerfiles and OCI container smoke checks may remain as build/CI qualification artifacts. They are not a local runtime requirement and are not used by the native Windows launcher.

## Supported replacement

Use the native Windows path:

- scripts/Start-UEP.ps1 — Demo, Real and Both modes.
- docs/operations/LOCAL_NATIVE_WINDOWS.md — setup, URLs, database ownership and operations.
- docs/operations/LOCAL_NATIVE_WINDOWS_TEST_PROMPT.md — full browser/API/PostgreSQL acceptance prompt.

The Real local path uses native PostgreSQL, Keycloak and Spring Boot processes. The Demo path is intentionally offline and fixture-backed. Neither mode calls the retired hosted backend or database.

## Decommission boundary — do not overstate completion

Completed and verified:
- Existing Fly API machine was reported stopped and has no attached volumes.
- No Keycloak Fly app exists.
- Supabase pause succeeded and the project subsequently reported INACTIVE.
- UEP Fly manifests and hosted database-migration workflow are removed from main.
- No hosted UEP database migrations were applied.

Not completed because account-level delete permission was unavailable:
- Deletion of the stopped Fly machine and Fly app record.
- Permanent deletion of the paused Supabase project.
- Any change to the existing Cloudflare Pages projects.

These remaining provider actions are intentionally not simulated or reported as complete.
