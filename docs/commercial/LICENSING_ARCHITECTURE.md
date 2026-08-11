# Commercial Licensing Architecture

## Goal

Make the same codebase commercially licensable across shared SaaS, dedicated,
private-cloud, and on-premise deployments without customer-specific source forks.

## Runtime decision model

`Tenant -> Subscription -> Entitlement -> Limit -> Usage -> Decision`

A plan/catalog may create these records, but runtime business code consumes only
resolved entitlements. This allows custom contracts and add-ons without spreading
pricing knowledge through academic modules.

## Subscription lifecycle

`TRIAL -> ACTIVE -> GRACE -> SUSPENDED -> EXPIRED`

`REVOKED` and `TERMINATED` are explicit terminal/control states. Expiration does
not imply immediate data deletion; retention/read-only behavior is policy driven.

## Feature keys in foundation

- `LIVE_CLASS`
- `RECORDING`
- `ASSESSMENTS`
- `AUTO_GRADING`
- `ADVANCED_REVIEW`
- `ASSIGNMENTS`
- `PAYMENTS`
- `CERTIFICATES`
- `API`
- `WHITE_LABEL`
- `SSO`
- `AI`

## Limit keys in foundation

- `ACTIVE_STUDENTS`
- `TEACHERS`
- `ADMINISTRATORS`
- `STORAGE_BYTES`
- `RECORDING_MINUTES`
- `CONCURRENT_MEETINGS`
- `MEETING_PARTICIPANTS`
- `SMS_MESSAGES`
- `AI_UNITS`

## Enforcement

A capability is allowed only if:

1. the request is authenticated and tenant-scoped;
2. subscription state currently permits new usage;
3. the feature entitlement is enabled;
4. applicable limit/usage checks pass;
5. domain-specific authorization independently passes.

Entitlements are not a replacement for RBAC/resource authorization.

## Commercial distribution

First-party code remains proprietary. Customer rights are granted through a
separate agreement and deployment-specific configuration. Distributed builds must
carry third-party notices/SBOMs. Private signing keys and provider credentials are
never embedded in source or browser bundles.
