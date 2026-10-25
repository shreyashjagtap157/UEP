# Integration Platform

The integration platform is an adapter layer around the canonical Universal Education Platform domain model. External standards and providers never become the internal persistence model.

## Public APIs

Tenant administrators can mint API credentials for a real, active tenant membership. A member may create a credential only for their own active membership; only an active Organization Owner or platform super-administrator may issue one for another membership. This prevents a caller with `API_MANAGE` from using a target membership ID to impersonate another user. Each credential must declare at least one permission scope, and effective API permissions are always the intersection of the current membership permissions and that explicit allowlist. Legacy credentials with an empty scope set are rejected during authentication rather than implicitly inheriting every current role permission. Secrets are shown once, only a SHA-256 hash is persisted, credentials can expire or be revoked, and each request is still constrained by the membership’s current role permissions. Usage timestamps are updated with an atomic metadata-only statement so concurrent requests using one key do not fight over the entity optimistic-lock version used by administrator revoke operations.

## Webhooks

Webhook subscriptions are tenant-owned. The delivery worker persists an event before transport, emits `X-UEP-Event-ID`, `X-UEP-Event`, and `X-UEP-Signature`, and retries with bounded exponential backoff. Signing secrets are encrypted at rest. Webhook and external-notification endpoints are validated at configuration and revalidated immediately before each send; endpoints resolving to loopback, private, link-local, reserved, or mixed public/non-public addresses are rejected, and redirects are disabled. These application checks do not pin DNS to the selected address, so production deployments must also apply network egress controls that prevent access to private networks and cloud metadata endpoints. Persisted delivery failures contain safe error categories or an HTTP status only; raw transport exception messages and callback query parameters are not stored. Interruptions restore the worker thread's interrupt flag. This also protects callbacks that use query parameters for short-lived credentials. The external-notification test-send path likewise logs only tenant/provider identifiers and the failure class, returns a generic error, preserves interrupt status, and does not attach raw HTTP exceptions as causes. Domain notification publication emits a public integration event; the integration module consumes it without creating a domain dependency cycle.

## SSO and federation

Tenant identity providers are represented as OIDC relying-party configuration. Authorization URLs include state and nonce supplied by the caller. This milestone deliberately stops short of becoming a second identity system: final authentication, token validation, and account linking remain under the trusted identity deployment. OpenID Connect is the baseline federation protocol; OpenID Federation 1.0 is a future trust-discovery extension.

## OneRoster

The adapter exports the canonical tenant model into a bounded subset of OneRoster 1.2 CSV resources and validates the required CSV envelope for future inbound adapters. OneRoster 1.2 defines separate rostering, gradebook, resource, and assessment-result services and supports both REST and CSV bindings.

## QTI

The QTI adapter handles `assessmentItem` XML as a foundation for interoperable question exchange. XML parsing disables external entities and DTDs. The current import intentionally maps only a safe baseline item representation into the platform question bank; broader QTI response-processing and assessment-test support belongs in subsequent interoperability qualification. QTI 3.0 is the current 1EdTech assessment-interchange baseline used here.

## Storage and notification providers

Existing local, S3-compatible, Google Drive, and Shared Drive adapters remain behind `StorageAdapter`. The new tenant binding chooses which configured adapter is the preferred external target without embedding provider-specific state into content or recording entities. Notification providers use an explicit provider key and type, with HTTP transport isolated from the core notification outbox.
