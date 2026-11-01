# Cloudflare demo deployment

The local client-demo build is a static React/Vite application and can be deployed to Cloudflare Pages on the Free plan. This is a presentation/demo deployment, not the authenticated multi-tenant UEP product.

## Pages settings

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

The repository connection alone cannot authorize deployment into a Cloudflare account. In Cloudflare Dashboard, use Workers & Pages → Create application → Pages → Connect to Git, select this repository, and enter the settings above. Cloudflare's Git integration then builds and deploys pushes to `main` and provides preview deployments for eligible branches.

The build command uses `npm ci --ignore-scripts` so the deployed bundle is built from the committed lockfile without executing dependency lifecycle scripts. The repository CI web job uses the same install mode. Cloudflare Pages supports pinning the build Node.js version through a `.node-version` file; this repository commits that version alongside the web package.

## Important production boundary

This deployment is only the presentation/local experience. It must not be described as the production multi-tenant backend deployment. The real product deployment and qualification procedure is `docs/operations/ACTUAL_STAGING_DEPLOYMENT.md` and requires the separately deployed backend, PostgreSQL database, Keycloak identity provider, storage provider, notifications, and authorization services to be configured and verified.
