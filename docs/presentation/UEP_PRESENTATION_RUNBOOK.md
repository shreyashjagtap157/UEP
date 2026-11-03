# UEP presentation runbook — Real implementation + offline demo

## Presentation goal

Present two honest views of the same project:

1. **Real UEP — local integrated build:** the current React application with a launcher-managed loopback session, Spring Boot API, separate backend Swagger UI and a local SQLite qualification database. Keycloak is not started in this local walkthrough.
2. **Offline presentation demo:** the curated role-specific seeded workspaces used for the concise academic workflow walkthrough.

These are two independent environments. The Offline Demo is not connected to the Real application and does not use its API session or SQLite database. The Real application is not a production deployment, and the SQLite profile is narrower than PostgreSQL production/CI qualification.

## One-command launch

From the repository root on Windows:

```powershell
.\startuep.ps1 -Mode Presentation -Database SQLite
```

In this paired SQLite presentation mode, the launcher creates or reuses only the Real application's local API authentication owner account. It intentionally does not provision the optional 13 backend test personas: the Offline Demo has its own independent seeded personas, and those broader membership/role workflows are outside the current SQLite qualification boundary.

Expected endpoints:

| Environment | URL | What it is |
|---|---|---|
| Real UEP | http://localhost:5173 | Local-authenticated React UI; API requests go through Vite to Spring Boot |
| Offline Demo | http://localhost:5174 | Seeded personas, simulated sign-in and browser-local sample state |
| Swagger UI | http://127.0.0.1:8080/swagger-ui.html | Separate interactive backend API reference (local-native profile only) |
| Spring Boot API | http://127.0.0.1:8080 | Local backend; generated OpenAPI JSON at `/v3/api-docs` |
| SQLite file | `.uep-local\uep-local.sqlite` | Local qualification data for Real UEP |

The Real UI displays `REAL UEP - LOCAL SQLITE`. The Demo UI displays `OFFLINE DEMO · SEEDED BROWSER DATA`. Keep the two windows separate during the presentation.

## Recommended run of show

Suggested total: 9–12 minutes plus questions.

| Segment | Time | Environment | Purpose |
|---|---:|---|---|
| Opening teaser | 30–45 sec | Offline Demo | Show the student-facing experience quickly |
| Four-slide deck | 90–120 sec | Slides | Motivation, platform scope, architecture, honest boundary |
| Real implementation preview | 3–4 min | Real UEP | Prove the currently integrated identity/API/database slice |
| Offline demo walkthrough | 4–5 min | Offline Demo | Show the role-specific seeded academic workflow |
| Closing | 15–20 sec | Slides / spoken | Explain next qualification milestones |

### Opening teaser — Offline Demo

Say: “Before I explain the architecture, here is a short preview of the student experience. This is a seeded offline presentation environment; it is separate from the connected application.”

1. Open the Demo window and sign in as Student.
2. Show Today, My Learning and one prepared assignment/result.
3. Point out the visible Offline Demo label.
4. Do not claim API, local API authentication, server-side RBAC or database persistence.

### Four-slide presentation

Use the established four-slide speaker guide:

1. Why UEP started — learner experience and the problem being addressed.
2. What UEP brings together — role-specific education and institutional workflows.
3. Technologies and architecture — React/TypeScript/Vite, Java/Spring Boot modular monolith, PostgreSQL/Flyway target, and the limited local SQLite qualification profile. External OIDC/Keycloak integration is not started for this local demonstration.
4. What will be demonstrated — the current real integration slice and then the separate seeded offline demo.

Keep the distinction explicit: source capability, demo behavior and runtime-verified behavior are three different things.

### Real implementation preview — Real UEP

Say: “This is the actual UEP application path. The frontend calls the Spring Boot API through the local launcher session, and the API persists the supported workflow in SQLite. The backend API reference is available separately in Swagger UI. This is a local qualification environment, not a production deployment.”

1. Open Real UEP. It loads the persisted local owner identity from the database through `/api/v1/me`; this local path does not use Keycloak.
2. Show the authenticated user, tenant identifier and assigned roles in the workspace.
3. Explain the request path: Browser → Vite same-origin `/api` proxy → Spring Boot → SQLite. The browser never connects directly to the database.
4. Open Organization and, after the local SQLite native smoke has passed, create a uniquely named branch. Reload the page and show that it is returned by the backend again.
5. Optionally show role/permission or security surfaces only after rehearsing those exact operations in this SQLite runtime. Do not use visible navigation or source-code presence as proof that every module works end to end.

The branch create/readback step is the current primary real integration proof for SQLite mode. If the native Windows smoke test has not been run successfully, do not claim that step as verified; show the screen as an implementation preview and call the live workflow unqualified.

### Offline demo walkthrough — seeded academic journey

Say: “I will now switch to the separate offline demo. These records are seeded sample data and changes stay in browser storage.”

1. Student Today — show role-specific sample metrics and pending tasks.
2. My Learning and Calendar & classes — show the seeded courses, materials, progress and schedule.
3. Assignments — submit one prepared assignment; say that this changes local demo state only.
4. Sign out and select Teacher — open Submission queue and grade the prepared sample submission.
5. Return to Student — show Results & feedback.
6. Optionally show sample Attendance or Inbox direct messaging.

Limitations to state when relevant:

- Inbox messaging is not the official announcement publisher.
- The offline demo does not provide a full question-by-question timed exam.
- Demo submission and grading are not production backend transactions.
- Do not demonstrate live media, external delivery or provider connectivity unless separately tested.

## What can be claimed

| Claim | Evidence boundary |
|---|---|
| Offline student/teacher workspace and seeded academic journey | Demo implementation; browser-local state only |
| Local API session and tenant identity in Real UEP | Real native SQLite browser test must pass on the presentation machine |
| Branch API write and readback after reload | Real native SQLite browser test must pass on the presentation machine |
| SQLite schema generation and tenant persistence | Backend SQLite integration test in CI |
| PostgreSQL migrations, UUIDv7 and restricted runtime grants | PostgreSQL CI matrix only; not proven by SQLite |
| Other domain workspaces (assessment, announcements, finance, integrations, analytics, live learning) | Describe as source implementation unless separately runtime-tested in the active local profile |
| Production readiness | Not established by this presentation runtime |

## Before presenting

- Sync the latest repository `main`.
- Run the launcher once in Presentation mode and confirm both windows display their correct environment labels.
- Open Real UEP and complete the local-authenticated branch create/reload/readback smoke.
- Prepare the Student and Teacher offline demo flow and verify the seeded records still appear.
- Close unrelated browser tabs; keep Real at :5173 and Demo at :5174.
- Keep local credential files private. Do not paste or put passwords into the presentation.
- Keep the launcher PowerShell window visible or minimized so service state can be checked.
- If the Real runtime fails, do not silently substitute Demo data and call it live. Continue with the offline demo and accurately describe the blocker.

## Current known qualification limits

- SQLite profile disables Flyway and PostgreSQL-specific background workers.
- SQLite is not a replacement for PostgreSQL production/CI qualification.
- The native Playwright Real-mode tests must be executed on Windows; Linux CI only validates test discovery.
- The full UEP implementation and production qualification are ongoing. This presentation demonstrates the current working slice and the curated offline role experience, not completion of the entire platform.
