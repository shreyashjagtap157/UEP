# Advanced Academic Platform

Milestone 0.13 adds outcome/competency tracking, reusable learning paths, verifiable credentials, mentoring, surveys, and feedback.

## Truth model
Academic facts remain in their authoritative modules. Advanced outcomes are mappings to those facts, not replacements for grades, attendance, assignments, or content. Mastery is evidence-bearing and versioned at the membership/competency level.

## Credentials
Credentials have tenant-scoped templates and immutable verification codes. Issuance and revocation are audited. Public verification returns only the minimum display data required to establish validity; opaque identity-provider subjects are not exposed. The QR payload is the canonical verification URL for future web/native QR renderers.

## Engagement
Mentoring is an explicit relationship state machine. Surveys use first-class questions with JSON response payloads, optional anonymity, and one identified response per membership. Feedback targets a typed domain object and is auditable.

## Client strategy
All business rules remain server-authoritative. The first client is the web application; native clients consume the same APIs later.
