# ADR 0007: Immutable grading and academic review

## Decision

Persist grading as append-only revisions and make academic review a first-class evidence workflow. The server remains the source of truth; the web client is the first production client.

## Consequences

- Historical grades remain reproducible.
- Publication changes state without destructive history mutation.
- Teacher intervention is explicit and auditable.
- Answer revision and challenge workflows can be reused by native clients.
- Regrading is a new revision, never an in-place rewrite.
