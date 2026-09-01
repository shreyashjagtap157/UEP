# Assessment Core — 0.6.0.0

Assessment is server-authoritative and tenant scoped. The web client is the first production client; future native clients consume the same API contracts.

## Bounded modules

- `questionbank` owns stable question identities and immutable question versions.
- `assessment` owns assessments, assessment versions, question-version attachments, batch assignment, attempts, answers, server timing, autosave and submission.

No assessment references mutable question content. Published construction uses an immutable `question_version` identity.

## Question model

Initial supported types are `SINGLE_MCQ`, `MULTIPLE_SELECTION`, `TRUE_FALSE`, `NUMERIC`, `FILL_BLANK`, `SHORT_ANSWER`, `LONG_ANSWER`, `ESSAY`, `MATCHING`, `ORDERING`, and `FILE_SUBMISSION`.

Question payloads are versioned JSON so the schema can evolve without changing the stable question identity. Scoring remains separate from the question payload and is prepared for the grading/review milestone.

## Exam lifecycle

`DRAFT -> PUBLISHED -> CLOSED -> ARCHIVED`

A published assessment cannot be edited in place. New construction belongs in a new assessment version. Each version records its availability window, duration, attempts, marks and navigation settings.

## Attempts

Attempts persist `started_at`, `expires_at`, and `submitted_at`. The server is the authority for expiry and browser time is display-only. Attempt writes are accepted only while the attempt is `IN_PROGRESS` and before `expires_at`.

Batch assignments are checked server-side before an ordinary user can start an attempt. Attempt reads/writes are tenant-scoped and membership-scoped.

## Autosave

Answer writes require an idempotency key. Repeated keys return the previously committed answer. Server sequence numbers increase monotonically per attempt, so stale client writes cannot overwrite a newer answer.

## Integrity

Composite tenant-aware foreign keys cover question versions, assessment versions, questions, batch assignments, attempts, memberships and answers. Unique constraints prevent duplicate question-version attachments, duplicate batch assignments, duplicate attempt numbers, duplicate answers and duplicate idempotency keys.

## Web-first product direction

The web application owns the first complete user experience. The platform API intentionally exposes stable assessment/question/attempt contracts so desktop and mobile clients can be added later without moving timing, assignment, authorization or assessment-state rules into client code.
