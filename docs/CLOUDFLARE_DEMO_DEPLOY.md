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
