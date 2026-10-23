# UEP Client Demo Interaction Matrix

The local presentation environment is a seeded scenario, not a static slideshow. Each persona has an independent role-scoped session. Changes must propagate through the shared demo state where the product relationship requires it, while unauthorized data remains invisible.

## Core scenarios

1. Student submits an assignment -> teacher sees the submission in grading -> teacher grades it -> student sees the result.
2. Teacher updates learner progress/attendance -> student sees the updated learner record; client outcome metrics recalculate.
3. Client changes a program/cohort operational action -> administrator sees the organization change; unrelated organizations remain unchanged.
4. Administrator changes catalog/course approval state -> teacher/student availability changes only for the affected course.
5. Developer publishes a release -> developer telemetry/release state updates; academic users do not receive developer controls.
6. Guardian sees only the linked learner's resulting progress/results and no other learner records.

## Isolation rules

- Student: own learning records, own submissions/results, assigned schedule and communication.
- Teacher: assigned classes/learners and grading workflow; never another teacher's private class roster unless explicitly assigned.
- Client: organization-owned programs, people and aggregate outcomes; no platform-wide private data.
- Product Developer: platform product/release/integration telemetry; no academic learner records unless a documented support capability exists.
- Administrator: tenant/platform administration according to permissions.
- Guardian: explicitly linked learner data only.

## Presentation acceptance gate

A demo is not considered complete when a page merely renders. Every scenario above must be executable and observable across the affected personas, and a role must be unable to navigate to or reveal another role's unauthorized records.
