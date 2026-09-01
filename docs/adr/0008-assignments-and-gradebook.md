# ADR 0008 — Assignments and Gradebook

Status: Accepted

The 0.8 milestone introduces assignments as explicit tenant-domain records, submission attempts as separate versioned work records, and a gradebook projection computed from assignment weights.

The platform uses basis-point weights (0..10000), server-side due-date evaluation, immutable submitted attempts, and optimistic persistence via JPA `@Version`. Batch assignment is limited to 100% aggregate weight so weighted grades remain interpretable.

Gradebook reads cross module boundaries only through `AssignmentDirectory`; direct repository access is prohibited. Native clients must consume the same API contracts after the web client has reached production readiness.
