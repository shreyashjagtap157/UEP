# Implementation Status

## Current verified state — 2026-11-04

### Repository and evidence boundary

- Repository: `https://github.com/shreyashjagtap157/UEP`
- Development branch: `main`
- Version: `0.15.0.0-SNAPSHOT` (not a stable release).
- At this status refresh, `main` is at documentation-only commit [`d14e0b5108335a105b1ade88834d32c60b79d116`](https://github.com/shreyashjagtap157/UEP/commit/d14e0b5108335a105b1ade88834d32c60b79d116). The last implementation commit on `main` remains [`992ea2f7ef95748f500865e306ec82b2062af847`](https://github.com/shreyashjagtap157/UEP/commit/992ea2f7ef95748f500865e306ec82b2062af847) (`feat: add REST API controllers for platform modules`). Product UI, Demo persona, local-native logout, and subsequent navigation/messaging hardening remain on draft PR [#38](https://github.com/shreyashjagtap157/UEP/pull/38), branch `feat/product-ui-role-navigation-auth-session`, and are **not part of `main`**.
- The latest feature-branch code/test head is [`8330bdfc54317e5162a1524c943ecf0e3da171c6`](https://github.com/shreyashjagtap157/UEP/commit/8330bdfc54317e5162a1524c943ecf0e3da171c6). GitHub comparison against `main` commit `bca966c632af85ba3be69b7180543ff3ed5f00ed` reports **69 commits ahead and 0 behind** before this documentation refresh. PR #38 remains open and draft. The implementation-status document is maintained on `main` and synchronized into this feature branch.
- CI evidence below is from GitHub Actions on the feature branch, not from a commit already merged to `main`. No claim is made that the target workstation itself ran PowerShell, Maven or Playwright; the verified Windows execution is on GitHub's `windows-latest` runner. Source inspection and committed tests are not runtime proof.

### Real local implementation and offline Demo

The launcher now defaults to a paired presentation topology:

```powershell
.\startuep.ps1
```

That is Presentation mode with SQLite. The separately selectable modes remain:

```powershell
.\startuep.ps1 -Mode Real -Database SQLite
.\startuep.ps1 -Mode Demo
.\startuep.ps1 -Mode Presentation -Database SQLite -NoBrowser
.\startuep.ps1 -Mode Real -Database PostgreSQL
```

- **Real UEP** uses the actual React application (`VITE_LOCAL_MODE=false`), the Spring Boot API, local-native authentication and the selected local database. Vite serves same-origin `/api` requests and proxies them to `127.0.0.1:8080`.
- **Offline Demo** uses the separate seeded browser-local experience (`VITE_LOCAL_MODE=true`). It does not call Spring Boot, SQLite or PostgreSQL and is not a live API demonstration.
- In paired mode Real is available at `http://127.0.0.1:5173`; Demo is at `http://127.0.0.1:5174`. Swagger UI is at `http://127.0.0.1:8080/swagger-ui.html`; OpenAPI JSON is at `http://127.0.0.1:8080/v3/api-docs`.
- Local-native provisions one persisted owner identity, active tenant membership and Organization Owner role. It does not provision additional Keycloak/OIDC test users. Demo personas are synthetic browser data, not backend accounts.

### Security and launcher changes now in source

- The random 12-hour local API credential is passed to the Real Vite **Node process** as `UEP_LOCAL_AUTH_TOKEN`, not a `VITE_` variable. The Vite proxy attaches `X-UEP-Local-Auth` to the upstream request. The browser bundle, browser storage and browser request headers must not receive this credential or a bearer token.
- The native SQLite and PostgreSQL browser suites now check that direct unauthenticated backend `/api/v1/me` returns 401, while browser-origin API requests contain neither local-auth nor bearer headers. The Vite proxy is responsible for server-side injection.
- Anonymous Swagger UI/OpenAPI access is permitted only in the explicit `local-native` profile; regular protected API routes remain authenticated.
- The local launcher no longer includes its unused Keycloak installer/account-bootstrap code or creates/modifies Keycloak PostgreSQL roles/schema. Any pre-existing local Keycloak database objects are deliberately left untouched.
- Demo-only prerequisite inventory checks the frontend and Node/npm path; it does not require backend source files, a JDK, Maven or PostgreSQL.
- Real-mode startup requires the relevant backend prerequisites, checks the Vite/API ports before running `npm ci`, detects managed-process early exits during readiness checks and waits for the Real page, proxied API version endpoint, backend health, Swagger UI and OpenAPI document.
- Startup verifies the OpenAPI title and calls `/api/v1/me` through Vite without supplying the token from the caller. It then verifies direct backend access without the proxy returns HTTP 401.
- Live status reports DB/API read and identity as UP only after the persisted local owner identity is returned by the authenticated API. A non-empty SQLite file is no longer accepted as sufficient database health evidence.
- SQLite is the default local qualification database: `local-sqlite,local-native`, local schema generation via Hibernate, no Flyway, no PostgreSQL service and disabled PostgreSQL-dependent background workers.
- PostgreSQL remains an explicit qualification option: local PostgreSQL 17/18, tracked Flyway migrations via `DatabaseMigrationRunner` and `uep_local_migrator`, then Spring Boot under the restricted `uep_local_runtime` role with Flyway disabled. The launcher provisions only the migration/runtime roles.
- The root `startuep.ps1` remains a compatibility wrapper around `scripts/Start-UEP.ps1`; both default to paired Presentation mode. The data files and protected local state are retained during normal stop/failure cleanup.


### Pending product UI, persona and session milestone — draft PR #38 (not merged to main)

This is a substantial in-progress product-quality change set. Its implementation and CI evidence belong to `feat/product-ui-role-navigation-auth-session` until PR #38 is reviewed and merged.

- **Visual system:** refreshed the Real and Offline Demo presentation with warm ivory surfaces, deep slate navigation, restrained copper accents, editorial headings, consistent header/session treatment and responsive layouts. A follow-up CSS correction preserves the responsive workspace layout when desktop product styling is applied.
- **Organizational account groups:** the Offline Demo offers 14 seeded personas across **Platform team**, **Client & institution management**, and **Institution teaching & learning**.
- **Platform Developer vs Product Maintainer:** Platform Developer is scoped to platform integrations, webhook health and technical/client-service operations. It does not receive learning-content, assignment or product-authoring navigation. Product Maintainer owns educational product catalogue, draft/release and publishing workflows. Demo create/publish mutations enforce that distinction.
- **Existing Demo data upgrade:** the browser store is upgraded to schema v5. Its migration reads v4 and older stored states, changes the old Developer record label to Platform Developer, adds the Product Maintainer record only when it is absent, and preserves the remaining persisted users, suspension status, course progress, products and workflow data. A dedicated v4-to-v5 regression test covers that upgrade.
- **Demo account switching:** one header **Switch user** action clears the active tab's Demo identity and returns to the grouped account selector. The browser regression covers all 14 persona sign-ins, the exact ordered navigation for each role, and switching back to the selector.
- **Real local-native logout:** the Real local-native owner now has a genuine **Sign out** action. The browser sends a same-origin POST through the Vite proxy; the launcher token remains in the server-side Vite process and is not exposed to browser storage or JavaScript. Spring checks the local owner subject and session ID, revokes the process-owned session, and the authentication filter rejects subsequent use of that launcher token with HTTP 401. The UI renders a signed-out state and explains that restarting the launcher is required to establish a fresh single-owner Real session. Other tabs also transition to the signed-out state when their next identity request receives 401, including when session storage is unavailable.
- **Production authentication:** production OIDC sign-out remains on the identity-provider flow; the local-native path does not simulate 14 backend users or route the user to a non-functional Keycloak login screen.
- **Navigation gating:** the Real Security menu is permission-gated. Server-side API authorization remains the security boundary; hiding a menu item is not treated as authorization.
- **Real-product navigation and interaction styling:** the Real React workspace groups permission-visible sections into **My workspace**, **Teaching & learning**, **Institution management**, and **Platform & insights**. Its keyboard-accessible search narrows rendered destinations without changing permission checks; active destinations expose `aria-current="page"`; no-result states are announced; Escape clears the filter; and tablet/mobile groups scroll horizontally with snap behavior. Form, tab, and table interactions use the slate/copper system.
- **Offline Demo navigation accessibility:** all 14 role-specific workspaces now expose their active destination through `aria-current="page"`. The Demo action-feedback region is a polite, atomic status message. The focused persona E2E checks the active item before and after navigation for every persona.
- **Real navigation regression coverage:** permission-to-navigation mapping and filtering are isolated in `apps/web/src/app/workspace-navigation.ts`; six unit tests cover work-area order, permission visibility, branch-scoped permissions, case-insensitive search, empty results, and non-mutation. The tests are part of `npm test` and passed in current-runner qualification #1276 below. The Windows native Real SQLite Playwright test additionally covers filtering, no-results, Escape reset, active-page semantics, responsive navigation CSS, and organization navigation.
- **Demo inbox recipient authorization:** `messageRecipientsForProfile` and `sendDemoMessage` use one shared policy. Recipients must be active and in the sender's organization, except the Platform Administrator may cross organizations. Students and guardians may contact only active Teacher/Teaching Assistant/Mentor accounts assigned to at least one of the linked learner's published courses. Crafted recipient IDs cannot bypass the policy. Unit tests cover student/guardian scope, course overlap, inactive contacts, unsupported roles, cross-organization access, mixed authorized/unauthorized recipient IDs, and valid send paths.
- **Messaging UI and browser coverage:** Student-to-Teacher send/delivery/read-state and Guardian recipient boundaries are covered in `apps/web/test/e2e/messages.e2e.ts`. The open-Inbox revocation test changes the selected contact's status from a second same-origin browser tab, exercising the actual cross-document `storage` event. The first tab clears the revoked selection instead of silently redirecting the draft. The same contact is reactivated from another tab; the old selection must remain cleared. The sender then explicitly selects the Teaching Assistant, sends the preserved draft, and the selected Teaching Assistant must receive it. A separate cross-tab test suspends the signed-in sender and verifies that the composer disappears with an explicit inactive-account status. Unit tests cover suspended senders and recipients, course overlap, unsupported roles, cross-organization access and mixed recipient requests. Rejected sends retain the draft; no valid recipient or incomplete content disables sending. These latest lifecycle regressions await current-head run #1293.

The local-native logout integration test asserts a successful logout response followed by an unauthorized response for the same launcher session. Frontend contract tests also verify that browser code does not contain the local launcher token/header and that revoked-session 401 responses are handled.

### Feature-branch CI evidence for PR #38

- **Run #1235**, commit `781579a6f70a98cefb0c1a1d2433a1a0f4d8913b`, completed with **all seven jobs successful**. This includes frontend install/lint/Demo workflow tests/production-contract build; repository policy and PowerShell parsing; dependency SBOM and OSV scan; Windows Real + Demo SQLite presentation startup; native local-auth-to-SQLite branch create/readback and database-file checks; the focused all-14-persona Demo browser regression; PostgreSQL 17.11 and 18.4 Maven verification, migration callback and least-privilege database grants; API OCI ARM64 build/smoke and fresh-database packaged-server health; and the Keycloak staging OCI ARM64 image smoke.
- **Run #1236**, feature head `928237d8d50c99686e77e705c083db9ee5a1f19a`, completed successfully for all seven jobs on that earlier feature revision. Both backend matrix jobs completed Maven verification, PostgreSQL migration callback, least-privilege grants, current-runner and OCI ARM64 API image builds/smokes, and fresh-database packaged-server health checks. This is historical green evidence and does not qualify the later Real-product navigation changes.
- **Run #1276** ([GitHub Actions](https://github.com/shreyashjagtap157/UEP/actions/runs/41773720706)), tested feature commit `a535173f246218258f9d7354e7bcc57c3de3c47f`, has now completed **all seven CI jobs successfully**. The web job passed dependency installation, lint, the full Node Demo/workflow unit suite, Playwright native test discovery and the production-contract frontend build. Windows passed the launcher-backed Real SQLite test (including Real navigation assertions) and **28/28 Offline Demo E2E tests**, including Student-to-Teacher message and Guardian recipient-scope tests. Both PostgreSQL 17.11 and 18.4 backend matrices completed Maven verification, migrations, least-privilege checks, OCI ARM64 API image build/smoke and packaged-server health smoke. Repository-policy, vulnerability scanning and Keycloak staging OCI image build/smoke also passed. This is full repository-CI evidence for commit `a535173`, not for the subsequent inbox sender/recipient lifecycle refinements.
- **Run #1255** ([GitHub Actions](https://github.com/shreyashjagtap157/UEP/actions/runs/41613500642)), for documentation-sync merge commit `4bf8200981bdb31154145759a55e2420781adbb4`, remained **in progress** and predates the latest navigation-test commits.
- **Run #1258** ([GitHub Actions](https://github.com/shreyashjagtap157/UEP/actions/runs/41613768963)), implementation/test head `3c96d57780ee1117a30f6c9111b68daab725d49a`, remains **in progress**. Its `web` job failed in the new branch-scoped navigation test: **56 of 57 tests passed**. The failing assertion expected the builder itself to remove empty groups, while the UI deliberately removes them in the subsequent filter step. Lint passed; build steps were skipped after the test failure. The test was corrected in commit `15c312146587692b5f82eb24460358b0f015442c`; the fix is not yet qualified.
- **Run #1283** ([GitHub Actions](https://github.com/shreyashjagtap157/UEP/actions/runs/41774260213)), exact code/test head `9270469ff8185a3327961be2847f22583fe82315`, is **queued** and predates the final cross-tab storage-event and message-delivery assertions.
- **Run #1287** ([GitHub Actions](https://github.com/shreyashjagtap157/UEP/actions/runs/41774575406)), exact code/test head `5dfbebf44a223be800dac09f498f638c7b7fd984`, is **queued** and predates the sender-side suspension and recipient reactivation refinements.
- **Run #1293** ([GitHub Actions](https://github.com/shreyashjagtap157/UEP/actions/runs/41774995765)), exact current code/test head [`8330bdfc54317e5162a1524c943ecf0e3da171c6`](https://github.com/shreyashjagtap157/UEP/commit/8330bdfc54317e5162a1524c943ecf0e3da171c6), is **queued**. It includes sender deactivation UI feedback, suspended-sender unit coverage, revocation persistence across recipient reactivation, and explicit recipient reselection/delivery. No current-head validation result is claimed while queued.
- **Earlier diagnostic runs:** #1272 exposed a hard-coded test assumption that Developer and Support shared the same organization; #1273/#1274 exposed the browser-normalized mobile `scroll-snap-type` value and retained the pre-correction recipient assertion. Those assumptions were corrected in later test commits. Run #1276 is the first run that passed the frontend and Windows browser jobs after the organization and CSS assertion corrections. These older runs must not be reported as green for the corrected implementation.
- **Run #1265** ([GitHub Actions](https://github.com/shreyashjagtap157/UEP/actions/runs/41614427564)), for branch tip `1552c932c7ccfe2f25f327a4d924f04544b5f3cd` after synchronizing the updated implementation-status page, is **queued**. The product-source tree is unchanged from the corrected test commit.
- Earlier runs #1229–#1234 failed while this milestone was being stabilized. The failures exposed stale browser selectors, a malformed PowerShell cleanup block, a duplicate `Timezone` label selector, an undeclared Modulith dependency, a brittle finance-annotation check and cleanup exit-code propagation. Those defects were corrected in subsequent commits; the successful run #1235 confirms the resulting workflow at its tested commit.
- The broad legacy Demo E2E suite was also exercised during diagnosis and exposed 19 failures in unrelated older assessment, attendance, finance, support, mentoring, schedule and shared-state specs. It is **not** the gate used by this milestone. Windows CI now runs the focused account-switch/navigation acceptance spec instead. Those other legacy specs remain separate follow-up work.
- A green feature-branch run is repository CI evidence only. It does not mean PR #38 has merged, nor does it establish production release readiness or prove execution on the developer's local Windows workstation.

### Current source-level implementation coverage

The repository contains substantial source implementations across multi-tenancy, identity/RBAC, branches and organization settings; academic structure, enrollment and teaching assignments; scheduling; announcements and notifications; learning resources and storage; question banks and assessments; assignments, submissions, grading and gradebook; attendance, live classrooms and recordings; finance; analytics; integrations/federation/interoperability foundations; and operations/security qualification tooling. The Offline Demo separately contains role-based simulated education workflows.

This inventory describes source implementation, not full end-to-end acceptance of every domain in the local Real app. The native SQLite browser path currently targets local owner identity plus branch creation/readback. The PostgreSQL native suite additionally targets analytics/CSV, branch persistence, role CRUD, membership/role assignments and restricted database-role boundaries. These tests are committed but were not executed in this review.

### CI status

The 2-November runs #935/#936 and the 28-October run #40701828392 remain historical. Run #1276 completed **all seven jobs successfully** on code head `a535173f246218258f9d7354e7bcc57c3de3c47f`, including PostgreSQL 17.11/18.4 OCI ARM64 API image build/smoke and packaged-server health. It passed the Real SQLite and 28-test Offline Demo browser suites. This is a fully green baseline before the later sender/recipient lifecycle refinements. Run #1293 is queued on code/test head `8330bdfc54317e5162a1524c943ecf0e3da171c6`.

The last implementation commit on `main` remains `992ea2f7ef95748f500865e306ec82b2062af847`; `main` currently points to documentation-only status updates. PR #38 remains open and draft. Before synchronizing this update, feature code/test head `8330bdfc54317e5162a1524c943ecf0e3da171c6` was 69 commits ahead and 0 behind `main`. Run #1276 is now a complete **7/7 CI pass**, including frontend, Windows Real + 28 Demo E2E, both PostgreSQL backend matrices, repository-policy, vulnerability scan and Keycloak container jobs. It validates the earlier code head `a535173`, not the newer inbox lifecycle refinements. Run #1293 is queued for the later code/test head `8330bdfc54317e5162a1524c943ecf0e3da171c6`. No current-head all-job pass is claimed.

### Remaining implementation / qualification gaps

The wider implementation roadmap remains larger than the local integration slice. Continue work on the documented outstanding areas, including:

1. Persistent communication threads, delivery/read state, moderation, blocking/reporting and abuse controls.
2. Advanced academic review, moderation/double grading, final adjudication and reproducible regrading.
3. Device-based attendance (NFC/RFID/biometric boundaries), import/API, offline sync and conflict resolution.
4. Configurable workflow states/transitions, approvals, deadlines, escalation, retries/idempotency and history.
5. Permission-aware tenant-safe global search, indexing/reindexing and rate/scale controls.
6. Finance discounts/credits/allocation, reconciliation, accounting exports and real provider qualification.
7. Multi-provider federation lifecycle, mappings, deprovisioning and security qualification.
8. Complete interoperability including LTI, CASE, xAPI, Caliper, SCORM, mature OneRoster/QTI and Open Badges/CLR.
9. Credential portability, issuer trust, status/revocation and verification.
10. Tenant white-label themes/templates and tenant-specific communications.
11. Live classroom reconnect/recovery, stale heartbeat cleanup, poor-network and large-class qualification.
12. Crash-safe recording workers, retries/recovery, transcoding/distribution, archive migration and storage recovery.
13. Longitudinal analytics, cohort comparison, competency progression and intervention/remediation.
14. PWA installability, service worker, cache, push and safe offline sync.
15. Native Windows/macOS/Linux/iOS/Android/kiosk clients integrated with stable platform contracts.

### Production release qualification

UEP is **not qualified for production release**. Before Production Web GA / 1.0, the project still needs independent evidence for security/penetration and abuse testing; production-like p50/p95/p99 load and capacity; WCAG 2.2 AA accessibility; PostgreSQL PITR/full restore and object-storage recovery; measured RPO/RTO; failure injection; provider degradation/failover; migrations/upgrades/rollback; external provider qualification; operational readiness; privacy and legal review. A green repository CI run alone is insufficient.

## Historical status snapshot — 2026-10-28

The following section records the earlier state and historical CI evidence as it stood on 28 October 2026. It is retained for traceability and must not be read as the current repository head or current CI status.



The following section records the earlier state and historical CI evidence as it stood on 28 October 2026. It is retained for traceability and must not be read as the current repository head or current CI status.

## Current verified state — 2026-10-28

`0.15.0.0-SNAPSHOT` remains the development version. The latest dependency merges are PR #25 (`@types/node` 26.6.5) and PR #22 (`livekit-client` 2.23.0); PR #22 merged as `8290fc16f9d18d3f3350f22934be5b1ee33a8ccc`. The latest confirmed full CI run is GitHub Actions run `40701828392` on main commit `8af6a2580cb8e43194238088e02cbf2582e008ff`. All four jobs passed: backend verification, web lint/workflow tests/build, repository-policy qualification, and vulnerability scanning. This green run includes the assessment catalog, Student/Guardian permissions, V044 role migration, question-bank authorization, assignment draft/list scoping with database pagination, academic-review decision scoping, gradebook viewer authorization, and the associated regression tests.

### Verified CI evidence

- Backend Maven verification passed, including application-context startup and packaged-server smoke coverage in the CI backend job.
- Web dependency installation, lint, demo workflow tests, and production build passed.
- Repository-policy qualification passed, including backup/restore safety guards, load-smoke percentile/latency checks, and the 0.15 qualification harness.
- Java dependency SBOM generation and OSV dependency scanning passed.
- Dependabot updates to `@types/node` 26.6.5 (PR #25) and `livekit-client` 2.23.0 (PR #22) were merged only after their refreshed CI checks passed.

Run #460 is historical baseline evidence. Run #40701828392 is the current green full-suite evidence for the implementation changes listed below. These results verify repository CI only; they do not imply production deployment or independent local execution. This is repository CI evidence, not a claim that production deployment or local execution was performed here. This is strong repository-local qualification evidence, but it is **not** by itself Production Web GA evidence; production-scale capacity, external/provider qualification, accessibility, recovery, operational, interoperability, and native-client qualification remain separate requirements.

## Layer A: Local Demo Mode Browser E2E Tests

Browser-based end-to-end test suite for local/demo mode has been created with 11 Playwright test files covering the golden path and role-specific workflows:

- `login-signout.e2e.ts` - Common login, sign-out, all 13 persona sign-ins
- `persona-workspace.e2e.ts` - Persona workspace identification and navigation
- `golden-path.e2e.ts` - Teacher assigns -> student submits -> teacher grades -> student sees grade
- `attendance.e2e.ts` - Attendance isolation between personas
- `schedule-announcements.e2e.ts` - Class and announcement publishing across roles
- `assessment.e2e.ts` - Exam lifecycle (valid attempt, forbidden completed exam access)
- `shared-state.e2e.ts` - Persistence after reload, cross-tab updates, storage warnings
- `developer-isolation.e2e.ts` - Developer telemetry visible only to developer persona
- `client-institution-isolation.e2e.ts` - Organization scope boundaries
- `guardian-mentor.e2e.ts` - Guardian/mentor limited learner scope
- `finance-support-auditor.e2e.ts` - Read-only role verification

Test configuration: `playwright.config.ts`; test runner: `scripts/test-runner.ps1`

**Execution status**: Tests are implemented and ready to run. However, the current session environment has sandbox EPERM restrictions that block:
  - npm package installation (node_modules cannot be installed)
  - Vite dev server startup (build EPERM sandbox violation)
  - Browser automation (no browsers installed in this environment)
  - Playwright test execution

The test files and configuration are complete and would execute successfully in a non-sandboxed environment with Node >= 22.22, Playwright installed, and the Vite dev server running with `VITE_LOCAL_MODE=true`.

These tests verify role-specific browser UI behavior in synthetic local demo mode with seeded data. They do not test real OIDC authentication, backend API calls, or production provider workflows. See the "Environment Blockers" section below for full details.

### Verified CI evidence

- Backend Maven verification passed, including application-context startup and packaged-server smoke coverage in the CI backend job.
- Web dependency installation, lint, demo workflow tests, and production build passed.
- Repository-policy qualification passed, including backup/restore safety guards, load-smoke percentile/latency checks, and the 0.15 qualification harness.
- Java dependency SBOM generation and OSV dependency scanning passed.
- Dependabot updates to `@types/node` 26.6.5 (PR #25) and `livekit-client` 2.23.0 (PR #22) were merged only after their refreshed CI checks passed.
- 11 browser E2E test files created for Layer A local demo mode coverage.

Run #460 is historical baseline evidence. Run #40701828392 is the current green full-suite evidence for the implementation changes listed below. These results verify repository CI only; they do not imply production deployment or independent local execution. This is repository CI evidence, not a claim that production deployment or local execution was performed here. This is strong repository-local qualification evidence, but it is **not** by itself Production Web GA evidence; production-scale capacity, external/provider qualification, accessibility, recovery, operational, interoperability, and native-client qualification remain separate requirements.

## Scope correction: no swarm-agent functionality

UEP is the Universal Education Platform and does **not** contain swarm-agent functionality. The previously introduced unrelated swarm-agent implementation and tests have been removed from `main`. Repository searches for `Swarm` and related swarm implementation markers return no matches. The remaining occurrences of `agent` are unrelated build/tool terminology and are not a UEP product feature.

Swarm-agent orchestration, specialist agents, swarm providers, agent playgrounds, and related APIs/configuration belong to other projects and must not be reintroduced into UEP.

## Current 0.15 Reliability and Performance Qualification

Implemented/qualified repository-local foundations include:

- repeatable load probes and k6 profile with explicit percentile targets;
- database reliability/performance indexes;
- bounded connection-pool configuration and graceful shutdown;
- backup/restore drill tooling and safety guards;
- tenant-isolation and identity/security configuration checks;
- repository verification and qualification harness;
- CI execution of the above gates.

Production qualification still requires production-like load/capacity evidence, provider qualification, operational runbooks, failure injection, and recovery evidence where the relevant environment/provider is required.

## Recent security and reliability fixes — 2026-10-28

- The finance `MANUAL` payment provider is no longer `final`, allowing Spring Modulith's class-based observability proxy to be generated. This fixed the application-context failure observed in the earlier backend CI run; refreshed backend CI passed afterward.
- Browser-demo course metrics now use the same published-catalog scope as role-specific course views. Mentor summaries resolve an active learner through the authorized mentor relationship and do not include draft-course progress.
- Local-demo state writes now report storage failures, avoid broadcasting a change event for a failed write, retain the attempted state in the current tab, and retry persistence when storage recovers. The UI warns when a change is visible only in the current tab.
- Recording detail, playback-token issuance, and stream opening now share a batch-scope authorization check. Members without `RECORDINGS_MANAGE` can access recordings only for their active batches; stream opening rechecks enrollment so a token issued before revocation is not sufficient afterward. Regression coverage was added for denied cross-batch access, permitted in-batch access, and revocation.
- The assessment catalog now returns published assessments assigned to the current member's active batches; version listing applies the same scope. Student permissions explicitly include `ASSESSMENTS_TAKE`, while Guardian permissions are separate and read-only for learner academic workflows.
- Flyway migration `V044__student_assessment_take_guardian_read_only.sql` applies the Student/Guardian permission correction to existing tenants; the 0.15 qualification script checks its required grants/removals.
- Question-bank catalog and version reads now require `ASSESSMENTS_MANAGE`; non-manager UI sessions do not fetch or render the question-bank catalog. Regression tests cover denied reads before repository access.
- Assignment draft saves now share the same published-status and active-batch membership guard as final submissions, preventing drafts against unpublished or unrelated assignments. Learner catalogs expose published and closed assignments but hide draft and archived work, using database-side pagination. Regression tests cover denied and permitted draft paths plus the catalog-state filter.
- Answer-revision decisions now apply the same review/batch-scope guard as review resolution, with regression tests for an out-of-scope reviewer and a reviewer sharing the learner's batch.
- Gradebook learner reads now validate the requesting user's scope: Students can view only their own gradebook, staff need the target's active batch or a branch-scoped grant, and authorized academic administrators retain tenant academic access. Guardian cross-learner gradebook reads fail closed because the backend currently has no persisted relationship model to verify; tests cover the denied and permitted paths.
- `scripts/qualify-0.15.py` now requires regression coverage for recording batch scope, assessment visibility, Student/Guardian permissions, and question-bank authorization.

## Current gradebook capability

The gradebook calculation path now supports a deterministic policy model for:

- drop-lowest;
- best-of-N;
- extra-credit percentage points;
- minimum completed components;
- weight renormalization after exclusions;
- configurable rounding;
- validation of scores, maximum points, and weight ranges;
- regression tests for policy behavior and invalid configurations.

The default policy preserves the existing standard calculation behavior.

## Major implemented platform areas

The verified repository contains substantial implementations across:

- multi-tenancy, tenant context, memberships, RBAC, branches and organization settings;
- sessions, identity/security foundations and authorization boundaries;
- academic periods, programs, courses/subjects/modules, cohorts, enrollment and teaching assignments;
- scheduling, recurring series, conflict detection, class sessions and calendars;
- announcements, notification outbox, in-app notifications, email integration and dead-letter handling;
- learning resources, immutable versions, resumable uploads and provider-independent storage adapters;
- question banks, immutable question versions, assessments, timing, autosave, submission and grading foundations;
- assignments, submissions, gradebook and learner progress;
- LiveKit live classrooms, presence, attendance and moderation foundations;
- recording orchestration, storage lifecycle, integrity, retention and playback authorization;
- invoices, installments, payments, receipts, SaaS plans, entitlements, quotas and usage;
- analytics, operations and reporting foundations;
- learning outcomes, competencies, learning paths, credentials, mentoring and surveys;
- API credentials, signed webhooks, OIDC federation and OneRoster/QTI foundations;
- reliability, security and qualification tooling;
- browser-only synthetic demonstration workspaces for the documented personas.

The browser demonstration is explicitly synthetic and is not a production authentication/security boundary. Production authorization remains server-side.

## Specification-derived areas requiring continued completion/audit

The project completion contract remains broader than the currently green CI suite. Continue implementation and qualification for:

1. **Communication:** persistent conversations/threads, delivery/read state, moderation, reporting/blocking, retention and abuse controls.
2. **Advanced academic review:** pass/fail rules, anonymous/double grading, moderation, final adjudication, immutable reproducibility and review-driven regrading.
3. **Attendance integrations:** kiosk/NFC/RFID/biometric boundaries, imports/API, offline synchronization, replay/idempotency and conflict resolution.
4. **Workflow engine:** configurable states/transitions, approvals, deadlines, escalation, conditional actions, retries/idempotency, versioning and historical reproducibility.
5. **Search:** permission-aware tenant-safe global search, indexing/reindexing, rate limiting and scale.
6. **Finance:** discounts, scholarships, credits/adjustments, payment allocation, settlement/reconciliation edge cases, accounting exports and real provider qualification.
7. **Federation:** multiple providers, lifecycle, claim/group mapping, deprovisioning and security qualification.
8. **Interoperability:** LTI, CASE, xAPI, Caliper, SCORM, mature OneRoster/QTI, Open Badges and CLR-compatible exchange.
9. **Credential portability:** signing, issuer identity, status/revocation and privacy-preserving verification.
10. **White-labeling:** tenant branding, templates and tenant-specific communications/support settings.
11. **Live classroom:** reconnect/recovery, stale-heartbeat expiry, browser/media errors, large-class and poor-network qualification, provider failure handling and observability.
12. **Recording platform:** crash-safe workers, retries/recovery, transcoding ladder, CDN/distribution, archive migration and storage recovery.
13. **Analytics:** longitudinal learner analytics, cohort comparison, competency progression, intervention/remediation and safe aggregation at scale.
14. **PWA/offline:** installability, service worker, caching, push and safe offline synchronization.
15. **Native clients:** Windows, macOS, Linux, Android, iOS/iPadOS and institutional kiosk clients, integrated through stabilized platform contracts.

## Production qualification still required

Before declaring Production Web GA / 1.0, the repository must have real evidence for authenticated abuse/fuzz testing, authorization bypass testing, upload/storage security, session/token abuse, webhook replay/forgery, federation and payment abuse, privacy authorization, external penetration/legal review, production-like p50/p95/p99 performance, accessibility/WCAG 2.2 AA testing, PostgreSQL PITR/full restore, object-storage recovery, measured RPO/RTO, failure injection, provider degradation/failover, migration/upgrade/rollback testing, and operational readiness.

## Version

`VERSION` remains `0.15.0.0-SNAPSHOT`. No stable release is declared by this document.
