# Identity and Organization Architecture

## Scope

Milestone `0.2.0.0` establishes the tenant identity and organization control plane used by every later academic domain.

## Identity boundary

Authentication is delegated to an OIDC provider. The platform does not store local passwords, OTP seeds, passkey private material, or identity-provider recovery secrets. It consumes signed identity claims and owns the application-side relationship between a global user and a tenant.

`user_account` represents the external identity subject. `tenant_membership` represents that person's participation in one client organization. Roles are many-to-many assignments, so one person can simultaneously be a teacher, evaluator, administrator, learner, or another supported role.

The trusted JWT `tenant_id` claim selects the request tenant. Browser headers and request bodies cannot override that boundary.

## Authorization model

Authorization is composed from:

1. authenticated OIDC identity;
2. active tenant membership;
3. role assignments;
4. persisted permission grants;
5. optional branch scope;
6. commercial entitlement checks in the owning domain.

Built-in roles are tenant-local persisted records rather than Java-only enums. Custom roles use the same role and permission tables. Later domains can add new permission keys without coupling authorization to commercial plan names.

The final active `Organization Owner` assignment cannot be removed and the final owner's membership cannot be deactivated. This prevents accidental tenant lockout.

## Tenant defense in depth

Memberships, role assignments, branch references, and platform sessions carry tenant-aware foreign keys. Service queries also require the current tenant. Database integrity therefore rejects cross-tenant relationships even if a future application defect constructs an invalid mutation.

## Sessions

Keycloak/OIDC remains responsible for identity-provider sessions. The platform additionally records the observed OIDC session identifier (or a token identifier fallback), last-seen time, token expiry, and application revocation state. Revoking a platform session immediately denies later API requests using the same observed session identity.

Session lists are paginated and tenant scoped. Administrative revocation is permission controlled and audit recorded.

## Strong authentication

The reference Keycloak realm enables TOTP and WebAuthn/passwordless passkey policy. The web client initiates Keycloak application-initiated actions for credential enrollment. The application records only authentication assurance evidence derived from signed token claims; it never receives or persists authenticator secrets.

## Organization model

Each tenant receives organization settings and can define multiple branches. Branch codes are unique within a tenant. Branch and settings updates use optimistic versions to prevent silent concurrent overwrite.

Settings currently include default IANA time zone, BCP 47 language tag, first weekday, support email, and HTTPS/HTTP support URL. These defaults are intentionally provider/client neutral and become inputs to later scheduling and communication modules.

## Bootstrap

The initial tenant owner is created through a platform-super-administrator-only bootstrap endpoint. Once an owner membership exists, ordinary tenant authorization governs identity administration. Production provisioning should automate the trusted `tenant_id` identity claim and remove interactive bootstrap access from normal operator workflows.
