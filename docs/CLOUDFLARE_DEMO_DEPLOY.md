# Cloudflare demo deployment

The local client-demo build is a static React/Vite application and can be deployed to a separate Cloudflare Pages project on the Free plan. This is a presentation/demo deployment, not the authenticated multi-tenant UEP product. Keep it separate from the real staging project so a local-mode build cannot overwrite the authenticated product frontend.

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

The repository connection alone cannot authorize deployment into a Cloudflare account. In Cloudflare Dashboard, use Workers & Pages → Create application → Pages → Connect to Git, select this repository, create the distinct project `uep-frontend-demo`, and enter the settings above. Cloudflare's Git integration then builds and deploys pushes to `main` and provides preview deployments for eligible branches. Do not point this local-mode project at the `uep-frontend-testing` project used by the real product staging runbook.

The build command uses `npm ci --ignore-scripts` so the deployed bundle is built from the committed lockfile without executing dependency lifecycle scripts. The repository CI web job uses the same install mode. Cloudflare Pages supports pinning the build Node.js version through a `.node-version` file; this repository commits that version alongside the web package.

## Important production boundary

This deployment is only the presentation/local experience. It must not be described as the production multi-tenant backend deployment. The real product deployment and qualification procedure is `docs/operations/ACTUAL_STAGING_DEPLOYMENT.md` and requires the separately deployed backend, PostgreSQL database, Keycloak identity provider, storage provider, notifications, and authorization services to be configured and verified.
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

    .\scripts\Start-UEP.ps1 -Mode Real

This starts the React client, Spring Boot API, Keycloak and PostgreSQL locally. See [LOCAL_NATIVE_WINDOWS.md](operations/LOCAL_NATIVE_WINDOWS.md) and [LOCAL_NATIVE_WINDOWS_TEST_PROMPT.md](operations/LOCAL_NATIVE_WINDOWS_TEST_PROMPT.md).

## Deployment boundary

A Pages static deployment contains frontend assets only. It is not a production UEP deployment and must never be presented as a connected multi-tenant education platform.
