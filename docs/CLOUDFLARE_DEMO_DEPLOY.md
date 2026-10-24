# Cloudflare demo deployment

The local client-demo build is a static React/Vite application and can be deployed to Cloudflare Pages on the Free plan.

## Pages settings

- Repository: `shreyashjagtap157/UEP`
- Production branch: `fix/client-demo-local-workspaces` until PR #17 is merged; then use `main`
- Root/build directory: `apps/web`
- Build command: `npm install && npm run build`
- Build output directory: `dist`
- Node version: `22.16.0` or newer
- Environment variable: `VITE_LOCAL_MODE=true`

The build is intentionally local/demo mode for the client presentation. It does not require Keycloak or the platform backend.

`apps/web/public/_redirects` provides the SPA fallback required for direct navigation to client-side routes.

## Cloudflare account step

The repository connection alone cannot authorize deployment into a user's Cloudflare account. In Cloudflare Dashboard, use Workers & Pages → Create application → Pages → Connect to Git, select this repository, and enter the settings above. Cloudflare's Git integration will then build and deploy each push and provide preview deployments.

## Important production boundary

This deployment is the presentation/local experience. It must not be described as the production multi-tenant backend deployment until the platform-server build, identity provider, database, storage, notifications and authorization services are deployed separately and connected.
