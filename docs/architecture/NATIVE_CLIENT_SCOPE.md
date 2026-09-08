# UEP Native Client Scope

## Product scope

UEP is a multi-client product. The web application (frontend + backend) is the first Production Web GA client, but the overall project also includes native client tracks for:

- Windows
- macOS
- Linux
- Android
- iOS/iPadOS
- institutional kiosk clients where required

Native clients are not separate business-rule implementations. They must consume the same server-authoritative contracts for identity, authorization, academic state, examinations, attendance, content, credentials, payments, licensing, notifications, media sessions, and offline synchronization.

## Architectural requirement

Native clients must preserve the existing architecture:

`CLIENTS → CONTRACTS/APIs → DOMAIN PLATFORM → DATA/STORAGE/MEDIA → INFRASTRUCTURE ADAPTERS`

Business rules remain on the server. Native clients may cache transport data and buffer explicitly supported offline events, but they must not acquire local authority over grades, credentials, payments, licensing, attendance decisions, or access control.

## Delivery sequencing

The existing implementation plan sequences native production applications after the first Web GA release so the shared contracts can stabilize first. This sequencing is retained unless the project plan is explicitly changed; it does not remove native clients from the overall product scope.

## Current repository state

The verified source bundle used for this handoff contains the server and web application plus the native-readiness contracts/protocol planning, but does not contain production Windows, macOS, Linux, Android, or iOS/iPadOS application source trees. Those client applications therefore remain a future implementation track in the current verified Git history.
