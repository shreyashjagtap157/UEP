# UEP Client Demo Interaction Matrix

The local presentation environment is a seeded scenario, not a static slideshow. Each persona has an independent role-scoped session. Changes must propagate through the shared demo state where the product relationship requires it, while unauthorized data remains invisible.

## Core scenarios (verified by browser E2E tests)

1. Student submits an assignment -> teacher sees the submission in grading -> teacher grades it -> student sees the result.
   - Verified by: golden-path.e2e.ts
2. Teacher updates learner progress/attendance -> student sees the updated learner record; client outcome metrics recalculate.
   - Verified by: attendance.e2e.ts
3. Client changes a program/cohort operational action -> administrator sees the organization change; unrelated organizations remain unchanged.
   - Verified by: client-institution-isolation.e2e.ts
4. Administrator changes catalog/course approval state -> teacher/student availability changes only for the affected course.
   - Verified by: schedule-announcements.e2e.ts
5. Developer publishes a release -> developer telemetry/release state updates; academic users do not receive developer controls.
   - Verified by: developer-isolation.e2e.ts
6. Guardian sees only the linked learner's resulting progress/results and no other learner records.
   - Verified by: guardian-mentor.e2e.ts

## Isolation rules (verified by browser E2E tests)

- Student: own learning records, own submissions/results, assigned schedule and communication.
- Teacher: assigned classes/learners and grading workflow; never another teacher's private class roster unless explicitly assigned.
- Client: organization-owned programs, people and aggregate outcomes; no platform-wide private data.
- Product Developer: platform product/release/integration telemetry; no academic learner records unless a documented support capability exists.
- Administrator: tenant/platform administration according to permissions.
- Guardian: explicitly linked learner data only.

## Presentation acceptance gate

A demo is not considered complete when a page merely renders. Every scenario above must be executable and observable across the affected personas, and a role must be unable to navigate to or reveal another role's unauthorized records.

## Browser E2E Test Suite

A maintained Playwright test suite (11 files) has been added to `apps/web/test/e2e/` for deterministic Layer A execution. Tests interact with the rendered application in a real browser, exercising the local/demo workspace with 13 seeded personas. Test runner script: `scripts/test-runner.ps1`. Tests require `VITE_LOCAL_MODE=true` and a running Vite dev server. See `playwright.config.ts` for configuration.
