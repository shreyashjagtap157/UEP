# Retired OCI + Cloudflare Pages + R2 deployment proposal

**STATUS: RETIRED — DO NOT DEPLOY.** This proposal was never an approved or deployed backend/database environment. All OCI VM, PostgreSQL, Keycloak, container, reverse-proxy, R2, secret, migration and API deployment instructions have been superseded and removed from the supported path.

The account owner requires no additional charges. No OCI VM, managed PostgreSQL instance, Keycloak service, R2 bucket or API host was provisioned through this proposal. The existing Cloudflare Pages projects are frontend-only and are not evidence of a connected platform.

The currently known hosted status is recorded in FLY_SUPABASE_STAGING.md: the Fly backend machine is stopped and has not been deleted; the Keycloak Fly app does not exist; the Supabase UEP Free project was paused and is INACTIVE but has not been permanently deleted. No UEP migrations were applied to that hosted database.

The supported replacement is LOCAL_NATIVE_WINDOWS.md, using the native PowerShell launcher at scripts/Start-UEP.ps1. It runs PostgreSQL, Keycloak, Spring Boot and Vite directly on Windows without Docker/Podman or hosted infrastructure. Use LOCAL_NATIVE_WINDOWS_TEST_PROMPT.md for complete browser/API/database integration qualification.

Do not reactivate or copy commands from this historical proposal. Any future hosted deployment would require a new architecture and cost review, and explicit owner authorization.
