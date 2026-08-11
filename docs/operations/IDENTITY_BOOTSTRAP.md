# Identity Bootstrap and Operations

## Development realm

The reference realm is `education` and the browser client is `platform-web`. Users must have a trusted `tenant_id` user attribute mapped into the signed token before tenant APIs can be used.

The web client uses Authorization Code flow with PKCE. Direct access grants and implicit flow are disabled in the reference client.

## Initial owner bootstrap

1. Create the tenant through the platform provisioning path.
2. Create/federate the intended owner in the identity provider.
3. Set the trusted tenant identifier claim for that identity.
4. Authenticate an operator holding the `platform_super_admin` realm role.
5. Call `POST /api/v1/identity/bootstrap-owner` with the owner's OIDC subject and profile data.
6. Verify that the owner can authenticate and receives `Organization Owner` permissions.
7. Remove unnecessary bootstrap/operator access from routine use.

The bootstrap operation refuses to create another initial owner after a tenant already has memberships.

## Credential enrollment

Passkeys and OTP are enrolled at the identity provider through application-initiated actions. Do not add credential secrets to this application's database, logs, audit payloads, or support tooling.

## Session incident response

A user can revoke their own observed platform sessions. Administrators with `SESSIONS_MANAGE` can revoke a tenant member's platform session. Every revocation is audit recorded. Upstream identity-provider logout/session termination should also be used for provider-wide compromise response.
