# Retired historical staging deployment record

**STATUS: RETIRED — DO NOT DEPLOY.** This file is retained only as an audit pointer. Its former Fly.io, Keycloak, Supabase, credential, region, secret, deployment and migration instructions are obsolete and must not be followed.

The owner requires zero additional charges. The Fly API machine was reported stopped in Sydney (machine d89565db307d58) with no attached volume; the Keycloak Fly app does not exist. The existing Supabase UEP Free project was paused successfully and later reported INACTIVE. No UEP migrations were applied to that hosted database.

The repository has removed the Fly API and Keycloak manifests, the hosted database-migration workflow and the remote staging E2E workflow. The stopped Fly machine and Fly app record have not been deleted from the Fly account, and the paused Supabase project has not been permanently deleted. See [FLY_SUPABASE_STAGING.md](FLY_SUPABASE_STAGING.md) for the verified decommission boundary and outstanding owner-side account actions.

Use [LOCAL_NATIVE_WINDOWS.md](LOCAL_NATIVE_WINDOWS.md) for the supported Windows-native local environment and [LOCAL_NATIVE_WINDOWS_TEST_PROMPT.md](LOCAL_NATIVE_WINDOWS_TEST_PROMPT.md) for local browser/API/PostgreSQL integration testing.
