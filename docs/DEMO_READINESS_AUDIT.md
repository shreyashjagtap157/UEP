# UEP product UI and demo readiness audit

Date: 2026-11-03
Baseline: `main` at `7683236833d274e9d688f58cb0768abe28395f5c`
Work branch: `feat/product-ui-role-navigation-auth-session`

This audit describes the product and identity boundaries introduced in this workstream. It does not claim that Windows runtime checks or browser tests have passed until the branch's CI jobs report success.

## Two application paths

| Path | Runtime and identity | Data boundary |
| --- | --- | --- |
| Real UEP | React/Vite on port 5173, Spring Boot on 8080, launcher-issued loopback session; one persisted local Organization Owner | Actual API and selected local SQLite/PostgreSQL database |
| Offline Demo | Separate React/Vite app on port 5174; seeded persona sign-in | Browser-local shared demonstration dataset; not connected to the Real API or database |
| Production | OIDC identity provider and API bearer-token authentication | Tenant-scoped server-side authorization and persistence |

The local-native Real path intentionally does not start Keycloak and does not provision synthetic Student/Teacher/Developer backend memberships. Its single owner session is process-managed by the Windows launcher. Stop the PowerShell launcher with Ctrl+C to terminate it. The Real header's **Open demo accounts** link opens the independent Offline Demo; it does not impersonate or replace the Real owner identity. Production OIDC keeps the normal Sign out action.

## Offline Demo: account groups and responsibilities

The Offline Demo exposes 14 named sample personas in three groups. The identities are fixtures for presentation workflows and are not production user records.

| Organizational group | Persona | Responsibility and visible workspace |
| --- | --- | --- |
| Platform team | Platform Developer | Platform engineering, integration configuration, webhook health and client-service operations; no course authoring, learning content or assignments |
| Platform team | Product Maintainer | Educational product catalogue, product drafts, release review, versioning and publishing |
| Platform team | Support Operator | Institution support queue and issue triage, with restricted academic-record visibility |
| Platform team | Platform Administrator | Platform-wide people, organizations, academic configuration and operational administration |
| Platform team | Auditor | Read-only audit and compliance activity |
| Client & institution management | Client / Institution | Institution overview, programs, people, service actions, delivery schedule and outcomes |
| Client & institution management | Finance Administrator | Institution invoice, payment and account-status records |
| Client & institution management | Exam Controller | Examination schedule, exam-linked submissions and examination operations |
| Institution teaching & learning | Teacher | Assigned classes, learner roster, submission review, grading, attendance and course materials |
| Institution teaching & learning | Teaching Assistant | Assigned classes, attendance and submission triage |
| Institution teaching & learning | Evaluator | Assigned evaluation and grading queue |
| Institution teaching & learning | Mentor | Linked learner progress, evidence and mentoring activity |
| Institution teaching & learning | Student | Own enrolled published courses, learning materials, assignments, results, schedule and attendance |
| Institution teaching & learning | Parent / Guardian | Read-only progress, results, attendance and communications for the linked learner |

### Real tenant roles versus platform-team personas

The connected tenant identity model separately defines Organization Owner, Organization Administrator, Branch Administrator, Academic Administrator, Exam Controller, Finance Administrator, Teacher, Evaluator, Teaching Assistant, Mentor, Student, Guardian, Support Operator and Auditor roles. Their menus are derived from the current identity's permission set, and API authorization remains enforced by Spring Security. Branch and academic managers therefore receive scoped tenant permissions rather than a hard-coded global menu.

Platform Developer and Product Maintainer are platform-team responsibilities shown as Offline Demo personas; Product Maintainer is not a tenant system role in the current `DefaultRoleSeeder`. This avoids representing a platform product-release responsibility as an institution membership.

## Identity switching and role visibility

- Offline Demo sign-in stores the selected persona in the current tab's `sessionStorage` after checking that its seeded account is active.
- The single **Switch user** action in the workspace header clears that account session and returns to the three-group selector. Choosing another account is an explicit sign-out followed by a new sign-in.
- Demo navigation is declared per persona in `SpecialistLocalDashboard`; hidden sections are not rendered in the menu.
- Product creation and publishing are guarded in `LocalDemoStore` and assigned to Product Maintainer. Platform Developer receives integrations and client-service operations, not the product publishing form or academic navigation.
- Real UEP filters navigation by permissions and the backend continues to enforce authorization. The local-native Organization Owner is expected to see broad tenant administration; it is not the Developer persona.

## Product design direction

The updated interface uses a warm ivory workspace, deep slate navigation, restrained copper accents, editorial serif headings, clearer account grouping, consistent session controls, and responsive persona cards. These are presentation-layer changes; they do not weaken backend permission checks.

## Validation and Windows run path

The repository CI contains a `windows-latest` presentation smoke that launches `.\startuep.ps1 -Mode Presentation -Database SQLite -NoBrowser`, checks both frontends, Spring Boot, the Vite API proxy, Swagger/OpenAPI and executes the native local-auth-to-SQLite browser test. This branch extends that Windows job to run the focused Offline Demo sign-in/account-switch Playwright spec after stopping the launcher processes. The spec signs in to all 14 seeded personas, asserts each persona's exact ordered navigation menu, and exercises Switch user back to the grouped account selector. It is intentionally scoped to this acceptance contract rather than running every legacy Demo E2E file, several of which currently contain unrelated stale selectors and assumptions.

Useful local checks:

```powershell
npm --prefix apps/web ci
npm --prefix apps/web run lint
npm --prefix apps/web test
npm --prefix apps/web run build:production
.\startuep.ps1 -Mode Presentation -Database SQLite
```

The assistant environment cannot execute PowerShell or Windows desktop browsers. The first PR run reached the Real + Demo launcher and both frontends, but its native SQLite browser test exposed a duplicate `Timezone` label selector; the Offline Demo test was skipped because that first test failed. The cleanup step also exposed a PowerShell syntax defect in the added CI cleanup block. Both are corrected in the follow-up commit. The Windows job now runs the dedicated account-switch/role-navigation browser spec with `always()` so a Real native smoke failure does not suppress independent Demo coverage. The broad legacy E2E suite was also exercised during the first pass and surfaced 19 unrelated existing failures across assessment, attendance, finance, support, mentoring, schedule and shared-state selectors/fixtures; those are not included in this focused acceptance gate and remain separate work. The most recent completed native SQLite smoke passed its API create/readback and SQLite-file checks. The latest Actions run must still confirm the targeted Windows spec and the complete end-to-end qualification. An empty or missing GitHub status is not a pass.

## Claim boundary

Seeded Demo interactions are browser-local fixture behavior. The local-native Real path proves only the API/database operations exercised by its native browser smoke. Other modules must be separately exercised before describing them as end-to-end verified. Production readiness additionally requires deployment, security, accessibility, performance, recovery and operational evidence.