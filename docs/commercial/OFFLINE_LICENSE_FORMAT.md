# Signed Offline License Format

## Purpose

Dedicated and on-premise customers may need entitlement enforcement without a
continuous connection to the vendor control plane. The licensing module therefore
supports a future `SIGNED_OFFLINE_MANIFEST` authority while preserving the same
runtime entitlement API used by SaaS.

## Cryptographic envelope

The foundation verifier uses Ed25519. A license distribution envelope contains:

- `keyId` — identifies a vendor public verification key;
- `payload` — exact license payload bytes;
- `signature` — Ed25519 signature over those exact payload bytes.

The envelope is verified **before** the payload is trusted or imported. Signing
private keys remain vendor-side and must never ship with the application. Public
verification keys may be distributed with the server or loaded from a trusted
configuration source, with key rotation supported by `keyId`.

## Payload contract planned for the licensing milestone

The signed payload will carry at least:

- license ID and monotonically increasing revision;
- tenant/customer binding;
- issued/not-before/expiry/offline-grace timestamps;
- deployment scope where contractually required;
- feature entitlements;
- hard/soft limits;
- optional add-ons;
- product/version compatibility bounds;
- revocation/refresh metadata where an online control plane is available.

The payload is intentionally separate from pricing-plan names. A negotiated
enterprise contract can therefore map to exactly the same feature/limit model.

## Anti-rollback rule

An imported signed manifest must not replace a higher accepted license revision
with a lower revision unless an explicitly authorized recovery procedure is used.
This prevents an old but validly signed license from silently restoring expired or
revoked entitlements.

## Availability rule

Licensing failures must degrade according to explicit contract policy. A temporary
control-plane outage must not randomly destroy customer data. Grace/read-only
states are distinct from entitlement to create new usage.
