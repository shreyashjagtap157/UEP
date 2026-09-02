# ADR-0010 — Integration Platform and Interoperability Boundary

## Status
Accepted — 0.14.0.0

## Decision
Keep external standards, authentication providers, storage systems, and notification transports outside the canonical domain model. Expose them through tenant-scoped adapters and control-plane configuration.

API credentials inherit platform RBAC and may be further narrowed by permission scopes. Webhooks are durable asynchronous deliveries with HMAC signatures and bounded retries. OIDC federation is represented as relying-party configuration rather than a competing identity store. OneRoster and QTI are adapters over the internal model.

## Consequences
The web client and future native clients can use the same API contracts. Provider swaps do not require domain migrations. Certification/conformance work can evolve independently from core academic persistence. Full end-to-end SSO callback/login orchestration and complete OneRoster/QTI conformance remain explicit qualification scope rather than being implied by the adapter foundation.
