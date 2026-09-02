# Live Learning

Version target: 0.9.0.0

The web application is the first production client. Live sessions use a provider-neutral control-plane module and a LiveKit media plane. API keys/secrets stay server-side. The browser receives short-lived participant JWTs only. LiveKit access tokens are JWT-based and encode room identity/capabilities; participant identity is an opaque membership-derived identifier rather than PII.

## Policies
- LIVE_CLASS_VIEW: view/join an authorized live class.
- LIVE_CLASS_MANAGE: create/start/end live classes.
- LIVE_CLASS_MODERATE: mute/kick control-plane state.
- LIVE_CLASS_CHAT: chat capability.
- PRESENCE_VIEW/PRESENCE_MANAGE: participant presence.
- ATTENDANCE_VIEW/ATTENDANCE_MANAGE: attendance results/finalization.

## Web client
The web client uses `livekit-client` 2.22.1. Adaptive stream and dynacast are enabled for normal sessions; low-bandwidth sessions select a reduced capture profile. LiveKit's current client SDK exposes `setCameraEnabled`, `setMicrophoneEnabled`, `setScreenShareEnabled`, `sendText`, and realtime room events used by the web workspace.
