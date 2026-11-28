# Cloudflare demo deployment

The local client-demo build is a static React/Vite application intended for a separate Cloudflare Pages project. This is a presentation/demo deployment, not the authenticated multi-tenant UEP product. Hosted backend/database staging has been retired; this guide does not describe or authorize a connected product deployment.

## Pages settings

- Cloudflare Pages project: `uep-frontend-demo` (`https://uep-frontend-demo.pages.dev`)
- Repository: `shreyashjagtap157/UEP`
- Production branch: `main`
- Root directory: `apps/web`
- Build command: `npm ci --ignore-scripts && npm run build`
- Build output directory: `dist`
- Node version: pinned by `apps/web/.node-version` to `22.22.0` (the package requires Node `>=22.22.0`)
- Environment variable: `VITE_LOCAL_MODE=true`

The build is intentionally local/demo mode for the client presentation. It does not require Keycloak or the platform backend. Its data is local/demo data and must not be used for real student, institution, or financial records.

`apps/web/public/_redirects` provides the SPA fallback required for direct navigation to client-side routes.

## Cloudflare account step

The repository connection alone cannot authorize deployment into a Cloudflare account. In Cloudflare Dashboard, use Workers & Pages → Create application → Pages → Connect to Git, select this repository, create the distinct project `uep-frontend-demo`, and enter the settings above. Cloudflare's Git integration can build and deploy pushes to `main` and provide preview deployments for eligible branches. Treat the project as a static Offline Demo only; it is not connected to an API, identity provider or database. Do not reuse it to represent a future authenticated product frontend.

The build command uses `npm ci --ignore-scripts` so the deployed bundle is built from the committed lockfile without executing dependency lifecycle scripts. The repository CI web job uses the same install mode. Cloudflare Pages supports pinning the build Node.js version through a `.node-version` file; this repository commits that version alongside the web package.

## Important production boundary

This deployment is only the presentation/local experience. It must not be described as a production multi-tenant backend deployment. `docs/operations/ACTUAL_STAGING_DEPLOYMENT.md` is a superseded hosted-staging proposal preserved for history, not an active deployment procedure. The supported local integrated runtime is documented in `docs/operations/LOCAL_NATIVE_WINDOWS.md`; no hosted product deployment is currently qualified.
# Cloudflare Pages — offline frontend demo only

This guide applies only to the static React/Vite presentation frontend. It does not deploy or provision the Spring Boot API, Keycloak or PostgreSQL.

## Scope

- Project root: apps/web
- Build command: npm ci --ignore-scripts && npm run build
- Build output: dist
- Node.js: 22.22.0 or later
- Build variable: VITE_LOCAL_MODE=true

The frontend uses LocalDemoStore/local fixtures in this mode. It makes no claim of real authentication, server-side authorization, backend connectivity or database persistence.

The existing Cloudflare Pages projects are separate from the local Real mode. Do not set remote backend or Keycloak URLs in this demo project. Do not add a database connection string, password, API secret or other backend credential to Pages environment variables or frontend bundles.

## Local alternative

For an integrated real-data test, do not use this offline Pages build. Run:

    .\startuep.ps1 -Mode Real -Database SQLite

This starts the React client and Spring Boot API with the local-native launcher-issued session and SQLite by default; it does not start or contact Keycloak. Select `-Database PostgreSQL` only for explicit local PostgreSQL migration/runtime-role qualification. See [LOCAL_NATIVE_WINDOWS.md](operations/LOCAL_NATIVE_WINDOWS.md) and [LOCAL_NATIVE_WINDOWS_TEST_PROMPT.md](operations/LOCAL_NATIVE_WINDOWS_TEST_PROMPT.md).

## Deployment boundary

A Pages static deployment contains frontend assets only. It is not a production UEP deployment and must never be presented as a connected multi-tenant education platform.
