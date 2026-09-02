# ADR 0009 — Live Learning and Web-First Media

## Decision
Use a provider-neutral live-class control plane with LiveKit as the initial media provider. The web application is the first production client. Native clients remain later API consumers.

## Security
LiveKit API keys and secrets remain server-side; the platform mints short-lived participant JWTs. Participant identity is opaque. This follows current LiveKit guidance.

## Reliability
Presence is recorded independently of media events through server heartbeats, while attendance is finalized from accumulated server-side presence evidence. This avoids treating a browser event alone as authoritative.
