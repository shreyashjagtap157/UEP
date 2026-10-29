# Demo Readiness Audit

Date: 2026-10-23

## Verified Local Presentation Path

The local presentation path is enabled by `apps/web/.env` with `VITE_LOCAL_MODE=true`.

- First screen: local login form.
- Authenticated Student session: Student-only workspace.
- Student feature areas shown: schedule, upcoming class, already conducted classes, future classes, exams/tests, results/feedback, class notes, and announcements.
- When offline, these areas show explicit unavailable/empty states and explain that the backend must be connected; no fabricated records are displayed.
- Client and Developer workspaces are not rendered inside an authenticated Student session.
- Sign out clears the local session and returns to login.
- Browser verification found no requests to `/api/`, Keycloak, or backend port 8080 in local mode.

The offline path contains no course, class, assessment, attendance, organization, billing, API-health, or deployment records. Connected mode must be used for real data and persisted workflows.

## Role Boundary

| Persona | Local entry | Local scope | Status |
| --- | --- | --- | --- |
| Student | Login role: Student | Student learning, schedule, assessments, notes, announcements | Verified locally |
| Client | Login role: Client | Institution summary, cohort, teaching coverage, entitlement | Verified locally; fixture-backed |
| Developer | Login role: Developer | Platform/integration summary and release modules | Verified locally; fixture-backed |
| Teacher | Main connected application role model | Assigned teaching workspaces in the non-local shell | Backend-dependent and not locally authenticated |
| Administrator roles | Main connected application role model | Permission-gated administration workspaces | Backend-dependent and not locally authenticated |

The local authenticated dashboard does not provide a role-switcher. Role selection occurs only at the local demo login boundary.

## Feature Status

| Area | Status | Evidence / limitation |
| --- | --- | --- |
| Login-first local flow | WORKING | Browser-tested login, validation, session, and sign-out |
| Student schedule and learning navigation | WORKING locally | Deterministic fixture rows and local action feedback |
| Student assessment/notes/announcements presentation | WORKING locally | Deterministic fixture rows and local action feedback |
| Client/Developer isolated local workspaces | WORKING locally | Separate role selection at login; no cross-role content in session |
| Real OIDC authentication | BLOCKED | Keycloak is not running in this environment |
| Real API persistence and cross-role state | BLOCKED | Spring backend cannot start until JDK/Maven/database dependencies are available |
| Server-side authorization and tenant isolation | UNVERIFIED locally | Existing connected application contains the authorization model; requires backend runtime/tests |
| Backend compilation/integration qualification | BLOCKED | Maven/JDK/database setup and known CI compilation failures require separate repair |

## Validation Commands

From `apps/web`:

- `npm run lint` passed.
- `npm run build` passed.
- Browser smoke tests passed for Student login, Student-only content, feature actions, Client/Developer isolation, and sign-out.
- Mobile layout was previously verified without horizontal overflow.

## Presentation Boundary

Use local mode for a truthful offline UI walkthrough. Do not describe fixture values as live enrollment, attendance, API health, billing, or deployment data. For a production-style demo, disable local mode only after the backend, Keycloak, database, seed accounts, and connected authorization tests are available.
