# Retired free-tier deployment guide

**STATUS: RETIRED — DO NOT FOLLOW.**

All earlier Railway, AWS, H2, Fly.io, Supabase, Vercel and free-tier deployment proposals in this file are obsolete. None is an approved or supported deployment path for this repository.

The owner requires every task and runtime to use only genuinely free resources and incur no additional charges. The prior Fly API machine is stopped; the Keycloak Fly app does not exist; the connected Supabase UEP Free project was paused and is inactive. The hosted backend/database deployment entry points were removed from main. Provider-side permanent deletion is not claimed; see [the decommission record](docs/operations/FLY_SUPABASE_STAGING.md).

The supported path is local:
- [Native Windows launcher](scripts/Start-UEP.ps1)
- [Local environment guide](docs/operations/LOCAL_NATIVE_WINDOWS.md)
- [Local full-stack acceptance prompt](docs/operations/LOCAL_NATIVE_WINDOWS_TEST_PROMPT.md)

The Demo mode is an offline presentation using local fixtures. The Real mode runs the actual Spring Boot API, Keycloak OIDC and PostgreSQL database locally. Neither connects to hosted infrastructure.
