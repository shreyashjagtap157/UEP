# Deployment status — superseded guide

This document is retained only to preserve the historical path. Its Railway,
AWS, H2, `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, and
`VITE_LOCAL_MODE=true` instructions are obsolete and must not be followed for
a real product deployment.

The prior Cloudflare Pages deployment described here was a local presentation
build. It did not prove a connected Keycloak identity provider, Spring Boot
backend, Supabase PostgreSQL database, durable storage, or real multi-tenant
product behaviour.

Use the current deployment authority instead:

- [Actual remote staging deployment and qualification](docs/operations/ACTUAL_STAGING_DEPLOYMENT.md)

**Acceptance rule:** LocalExperience and LocalDemoStore are not product
identity/data providers and their tests do not qualify deployment. UEP is
accepted as a real staging product only after the frontend, backend, deployed
Keycloak realm, isolated Supabase database, staging identities, role matrix,
and cross-role feature workflows have all been verified against the remote
deployment.
