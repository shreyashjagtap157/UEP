# ADR 0003: Tenant identity, RBAC, and application session boundaries

- Status: Accepted
- Milestone: 0.2.0.0

## Decision

Use an external OIDC identity provider for authentication and a platform-owned tenant membership/RBAC model for authorization. Persist roles and permission grants per tenant, support multiple simultaneous roles per person, and add platform-side revocable session observations keyed by OIDC session identity.

## Consequences

- No passwords, OTP secrets, or passkey key material are stored by the education platform.
- Tenant authorization is independent from a particular identity provider implementation.
- Custom roles do not require source-code forks.
- Commercial entitlements remain a separate authorization dimension and are not encoded as role names.
- Cross-tenant membership, assignment, branch, and session relationships are rejected by database constraints.
- An application session can be denied immediately even before an upstream OIDC session naturally expires.
- Enterprise federation can replace the reference Keycloak deployment without rewriting academic authorization.
