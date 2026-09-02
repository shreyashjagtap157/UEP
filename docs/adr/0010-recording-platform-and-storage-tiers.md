# ADR-0010: Recording Platform and Storage Tiers

- Status: Accepted
- Date: 2026-09-02
- Version target: 0.10.0.0

## Context

Class recordings are long-running media workloads. Keeping capture, large binary storage, playback authorization, retention, and provider APIs inside the scheduling/live-class HTTP request path would make failures difficult to isolate and would prevent later replacement of media or storage vendors.

The platform already has stable `class_session` anchors, a provider-independent storage adapter boundary, tenant-aware authorization, durable audit, and notification infrastructure. The recording milestone must reuse those boundaries.

## Decision

Recordings are a first-class Spring Modulith domain module. PostgreSQL owns recording metadata, lifecycle state, quality, storage tier/provider, integrity data, egress identifier, retention timestamps, and asset metadata.

LiveKit Egress is integrated behind `RecordingMediaProvider`. Capture is asynchronous and reconciled by scheduled worker jobs. Media is first written to a shared processing volume, then finalized through `StorageAdapter`.

Storage lifecycle has three explicit tiers:

1. `HOT`: the initial provider immediately after recording finalization.
2. `CACHE`: an intermediate provider for the configured cache window.
3. `ARCHIVE`: the long-lived provider after the cache window.

Master and thumbnail assets move together. Migration is copy-then-delete, preserving the prior locator until the destination write succeeds.

Playback uses short-lived, membership-bound signed tokens. The browser receives no provider credentials or raw storage locator. The web client shows a moving identity watermark during playback; this is a visible deterrent, not DRM.

Retention is enforced by a background worker. Deletion is represented by durable metadata state and object deletion after the configured grace period.

## Consequences

The recording API remains responsive regardless of recording duration. Provider changes can be isolated behind two interfaces: media capture and object storage. Lifecycle operations become observable and auditable, while tenant isolation stays server-authoritative.

The reference deployment now requires a LiveKit Egress worker and Valkey alongside the LiveKit server. `ffmpeg` is an operational dependency for generated thumbnails. `SOURCE_ARCHIVE` is deliberately defined as the best supported composite provider preset instead of promising raw source-track preservation when the selected media backend cannot provide it.

The web player currently streams the finalized MP4 through a tokenized endpoint without HTTP range optimization; range-aware streaming and deeper CDN/cache integration remain follow-on hardening rather than hidden claims of completion.
