# Retired Vercel deployment candidate

**STATUS: RETIRED — NOT A SUPPORTED DEPLOYMENT TARGET.**

The former Vercel candidate depended on a containerized Spring Boot service, remote OIDC identity and a provisioned remote database. No Vercel project or backend/database resource was deployed or qualified through this candidate.

To enforce the zero-additional-charge requirement, the Vercel multi-service configuration, backend runtime profile and Vercel-specific build verifier have been removed. The repository no longer describes or configures a Vercel-hosted backend or database.

The Cloudflare Pages projects are static frontend-only projects; the demo project uses offline UI fixtures and is not a connected real-data product.

Use [LOCAL_NATIVE_WINDOWS.md](LOCAL_NATIVE_WINDOWS.md) for the supported native local runtime. It starts PostgreSQL, Keycloak, Spring Boot and Vite without Docker/Podman or hosted infrastructure. Use [LOCAL_NATIVE_WINDOWS_TEST_PROMPT.md](LOCAL_NATIVE_WINDOWS_TEST_PROMPT.md) for the full browser-to-API-to-database test plan.
