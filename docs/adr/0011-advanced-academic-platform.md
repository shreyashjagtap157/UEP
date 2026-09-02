# ADR 0011 — Advanced academic outcomes and credentials

## Status
Accepted for milestone 0.13.0.0.

## Decision
Learning outcomes and competencies are modeled as an explicit evidence layer mapped to authoritative academic targets. Learning paths provide reusable ordered learning journeys without replacing enrollment, content, assessment, grading, or attendance records.

Credentials are tenant-scoped, auditable, revocable records with globally unique verification codes and a canonical verification URL payload. Public verification exposes only minimum credential display data.

Mentoring, surveys, and feedback are separate engagement records with explicit lifecycle/status fields and tenant isolation.

## Consequences
The platform can report mastery by learning outcome and can later export portable credentials without coupling the core academic model to a specific badge standard or QR rendering library.
