# Recording Platform

Version target: 0.10.0.0

The recording subsystem is a server-authoritative control plane over long-running media work. PostgreSQL stores recording metadata and state; media bytes remain in a configured storage adapter. LiveKit Egress is an implementation of the media-capture boundary, not a persistence dependency of the domain model.

## Control plane

A recording belongs to one tenant, live classroom, and stable class-session anchor. A management-authorized user requests a quality preset while the classroom is live. The request is persisted as `REQUESTED` and a scheduled worker starts/reconciles capture without tying the HTTP request to the duration of the recording.

Processing states are explicit: `REQUESTED`, `STARTING`, `RECORDING`, `FINALIZING`, `READY`, `ARCHIVING`, `ARCHIVED`, `FAILED`, and `DELETED`. Provider failures become durable `FAILED` state with bounded failure text; reconciliation polls the provider until completion rather than trusting the initial start response.

## Media boundary

`RecordingMediaProvider` keeps LiveKit-specific APIs out of the domain service. The reference implementation uses LiveKit Egress room-composite MP4 capture. The Egress service is separately deployed and writes into a shared recording volume; the platform worker then hashes, persists, thumbnails, and publishes the finalized object through the normal `StorageAdapter` contract.

The deployment therefore requires the Egress container and the application worker to agree on the processing-volume path. The `/recordings` path in the reference compose deployment is shared by `livekit-egress` and the platform's configured `processing-root` mapping.

## Quality presets

- `ECONOMY`: H.264 720p/30.
- `BALANCED`: H.264 720p/30.
- `HIGH_QUALITY`: H.264 1080p/30.
- `SOURCE_ARCHIVE`: currently uses the highest supported composite preset in the provider implementation; source-track archival remains provider-dependent rather than falsely promising untouched source files.

## Storage lifecycle

The default policy keeps new media in `HOT` storage for 14 days, moves it to `CACHE` for 30 days, and then archives it. Each tier has its own provider selection, so local/NAS-like filesystem storage, S3-compatible object storage, Google Drive, and Shared Drive can be used at different lifecycle stages.

Master and thumbnail assets migrate together. Cross-provider migration is copy-then-delete: the destination object is written and the metadata locator is updated before the old object is deleted. The recording remains in its prior usable state when a migration fails. Retention is separate from archival and marks metadata `DELETED` only after the configured retention period plus deleted-object grace period.

## Playback protection

Playback is never an unprotected storage locator. The server checks tenant ownership, recording readiness, the current membership, and `RECORDINGS_VIEW`, then issues a five-minute HMAC-signed token bound to both recording and membership. The stream endpoint revalidates the token before opening the storage adapter.

The web player overlays the authorized learner/member identity and changes its screen position periodically. This is an intentional visible deterrent, not DRM and not a claim of absolute protection against screen capture or other client-side copying.

## Integrity and notification

The finalized master object receives a SHA-256 digest, persisted size and duration. A thumbnail is generated with `ffmpeg` when the binary is available; thumbnail failure does not invalidate a successfully stored master. A durable `RECORDING_READY` notification is published to active batch participants after finalization.

## Operations and failure handling

Worker intervals are configurable through `platform.recording.*`. The worker establishes tenant execution context before processing a recording so background jobs retain the same tenant isolation contract as HTTP requests. Storage-policy changes use optimistic versioning and are audited.

LiveKit capture, storage migration, and thumbnail processing are separate failure domains. A provider outage does not cause a request thread to wait for a media lifecycle, and a failed migration leaves the existing recording locator intact for a subsequent retry.

## Security and licensing

Recording permissions are first-class RBAC permissions (`RECORDINGS_VIEW`, `RECORDINGS_MANAGE`). The domain remains subscription/entitlement-ready because access is controlled through the platform authorization boundary rather than frontend feature switches or provider credentials exposed to the browser.
