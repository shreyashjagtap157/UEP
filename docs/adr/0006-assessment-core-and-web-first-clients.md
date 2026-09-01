# ADR 0006 — Assessment Core and Web-First Clients

## Decision

Build assessment capabilities in the shared server platform first and build the web application as the first-class client. Native clients are subsequent consumers of the same stable APIs.

## Consequences

- Exam timing, assignment, state transitions and authorization remain server authoritative.
- Question versions are immutable references suitable for reproducible future grading.
- Web UI can evolve independently without becoming the source of academic rules.
- Native applications can later reuse the same assessment contracts instead of duplicating domain logic.
