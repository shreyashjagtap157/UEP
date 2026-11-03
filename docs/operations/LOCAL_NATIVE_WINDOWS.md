# Native Windows local development and presentation

## Purpose

The native launcher can show either the real integrated UEP application, the curated offline presentation demo, or both side by side. It uses local Windows processes and **does not use Docker, Podman, Kubernetes, containers, a cloud database or a paid execution service**; no hosted database or production deployment is needed.

The implementation entry point is `scripts/Start-UEP.ps1`, with the root `startuep.ps1` acting as a compatibility wrapper.

The two experiences are intentionally separate:

- **Real UEP**: React/Vite → Keycloak OIDC → Spring Boot API → selected local database. API requests use the same-origin Vite proxy. This is the real application path, not a production deployment.
- **Offline Demo**: seeded role personas and browser-local sample state. It does not call Spring Boot, Keycloak, PostgreSQL or SQLite.

Never present a demo fixture update as a server/database write. Never describe the current Real local SQLite profile as full PostgreSQL or production qualification.

The root-level `startuep.ps1` is a compatibility wrapper for `scripts/Start-UEP.ps1`.

## Modes

| Mode | Frontend | Backend | Identity | Database | Data boundary |
|---|---|---|---|---|---|
| `Demo` | Demo: `http://localhost:5173` | Not started | Simulated persona selection | Not started | Seeded browser-side data only |
| `Real` | Real: `http://localhost:5173` | `http://localhost:8080` | Keycloak: `http://localhost:8081` | SQLite by default; PostgreSQL optional | Authenticated API and local persistence |
| `Both` | Real: `http://localhost:5173`; Demo: `http://localhost:5174` | `http://localhost:8080` | Keycloak: `http://localhost:8081` | SQLite by default; PostgreSQL optional | Separate Real and offline Demo apps |
| `Presentation` | Real: `http://localhost:5173`; Demo: `http://localhost:5174` | `http://localhost:8080` | Keycloak: `http://localhost:8081` | SQLite by default; PostgreSQL optional | Presentation-labelled paired apps |

`Presentation` is the recommended command for a walkthrough. It starts two separate Vite processes and opens the Real and Demo URLs independently. Browser storage and sign-in state are origin-scoped; Real and Demo do not share sessions or records.

## Requirements

For `Demo` only: Windows PowerShell, Node.js 22.22.0+, npm and the repository frontend dependencies.

For `Real`, `Both` or `Presentation`:

- x64 Windows and PowerShell 5.1 or PowerShell 7.
- JDK 21+ and Maven 3.9+.
- Node.js 22.22.0+ and npm.
- Keycloak 26.7.5 (the launcher obtains the official native ZIP and checks its pinned SHA-256).
- SQLite mode is the default and does not require PostgreSQL, psql, a PostgreSQL service, containers or a cloud database.
- PostgreSQL 17 or 18 is optional and selected explicitly with `-Database PostgreSQL`.

Maven and Keycloak are downloaded into the ignored `.uep-local/tools` directory. The frontend lockfile is installed with `npm ci`. The launcher does not change machine-wide execution policy.

## Start the paired presentation

From the repository root:

```powershell
.\startuep.ps1 -Mode Presentation -Database SQLite
```

This opens:

- **Real UEP** — `http://localhost:5173`
- **Offline Demo** — `http://localhost:5174`
- **Keycloak** — `http://localhost:8081`
- **Spring Boot API** — `http://localhost:8080`

The Real UI displays `REAL UEP - LOCAL SQLITE`. The Demo UI displays `OFFLINE DEMO - SEEDED BROWSER DATA`. Keep the two windows separate during the presentation.

For **every Real-mode SQLite launch** (`Real`, `Both` or `Presentation`), the launcher provisions only the Real application's local OIDC owner account. It skips the optional backend test-persona/membership/role seeding because those SQLite workflows have not yet been qualified. In `Both` and `Presentation`, the separate Offline Demo supplies its own browser-only seeded personas. The broader backend test-persona bootstrap remains available when PostgreSQL mode is explicitly selected.

Other supported commands:

```powershell
.\startuep.ps1 -Mode Real -Database SQLite
.\startuep.ps1 -Mode Demo
.\startuep.ps1 -Mode Presentation -Database PostgreSQL
.\startuep.ps1 -Mode Presentation -Database SQLite -NoBrowser
```

Use PostgreSQL only when it is installed and intentionally selected. The PostgreSQL mode retains the primary Flyway migration and separate migration/runtime/Keycloak-role design.

## SQLite local qualification boundary

SQLite mode:

- uses `.uep-local\uep-local.sqlite`;
- selects Spring profile `local-sqlite`;
- disables Flyway and creates/updates a local schema from JPA entity mappings;
- uses Keycloak's separate `dev-file` development store;
- disables background workers that depend on PostgreSQL-specific advisory locks;
- is intended for constrained local functional qualification, not production deployment.

It does **not** run the 50 PostgreSQL Flyway migrations, create PostgreSQL roles/grants, or validate PostgreSQL locking, migration, row-level privilege, concurrency, backup/restore or production behavior. PostgreSQL remains the production and CI database target. Not every UEP module has been qualified against SQLite; avoid claiming otherwise.

## Real UEP presentation walkthrough

The Real window is the application with OIDC authentication and API-backed pages. Browser requests to same-origin `/api` are proxied by Vite to the Spring Boot API at `http://127.0.0.1:8080`; the browser never connects directly to SQLite or PostgreSQL. It is not the fixture-based LocalExperience. Recommended sequence:

1. Complete the Keycloak login with the local owner account created by the launcher. Credentials are stored in the protected current-user LocalAppData folder; do not copy them into slides, shared documents or logs.
2. Show the signed-in identity, tenant context and role-specific navigation. Explain that the browser calls the Spring API through Vite's same-origin `/api` proxy.
3. Open **Organization** and demonstrate a branch create/readback only after the SQLite native smoke test has passed on this machine.
4. Show the current identity, role/permission and security surfaces that are available to this account. Only demonstrate actions that you have rehearsed against this SQLite runtime and have seen succeed through the API.
5. Explain that source modules for announcements, assessments, integrations, analytics and other domains are broader implementation work; source presence or menu visibility alone is not proof that each workflow is SQLite-qualified.

The checked-in SQLite backend integration test proves Hibernate startup/schema generation, a tenant row write/read and the SQLite-aware tenant existence guard. The dedicated native SQLite Playwright test is intended to prove actual Windows OIDC login, branch API write/readback after reload and the SQLite file signature. CI verifies test discovery but does not execute that native Windows test. Until it has been run locally, mark the real browser/API path **NOT TESTED on this workstation**.

## Offline demo presentation walkthrough

The Demo window is intentionally separate from Real. Use the seeded personas and show only the workflows that exist in the current build:

1. **Student Today** — role-scoped landing view and sample pending work.
2. **My Learning and Calendar & classes** — seeded courses, materials, progress and schedule.
3. **Assignments** — submit one prepared sample assignment. This updates browser-local demo state only.
4. **Teacher Submission queue** — sign out, select Teacher, and grade the prepared sample submission.
5. **Student Results & feedback** — return to Student to show the demo result state.
6. **Attendance or Inbox** — optionally mark sample attendance or show/send direct demo messaging.

The Demo workspace has an always-visible label: `OFFLINE DEMO · SEEDED BROWSER DATA`. Its login is simulated, and its shared data is held in browser storage. Its Inbox is direct messaging, not the official announcement publishing workflow. It does not contain the full question-by-question timed examination flow.

## Test commands

For the real local browser smoke, run in a second PowerShell window after the launcher reports services healthy:

```powershell
npm --prefix apps/web exec -- playwright install chromium
npm --prefix apps/web run test:e2e:native
```

The native suite selects the matching database-specific spec from the protected local state:

- SQLite: OIDC login, tenant identity, branch creation, browser reload/readback and the local SQLite file signature.
- PostgreSQL: OIDC/analytics/CSV, branch persistence, custom-role CRUD, membership/role assignment lifecycle, and restricted runtime-role database checks.

The SQLite suite is narrower; do not imply that it proves PostgreSQL grants or all business workflows. The native browser run must be performed on the actual Windows machine and the same Windows user profile that initialized UEP.

For broader verification, run `mvn -f apps/platform-server/pom.xml verify`, `npm --prefix apps/web test`, `npm --prefix apps/web run lint` and `npm --prefix apps/web run build`. CI results and local Windows results are separate evidence.

## Safety and data preservation

- The launcher does not connect to hosted or production databases.
- SQLite files, local Keycloak state and credentials remain local. Do not commit `.uep-local`.
- The launcher does not reset or delete user data automatically.
- Do not expose the protected LocalAppData credentials file.
- Never use Demo personas to imply real OIDC authentication, server-side RBAC or database persistence.
- Never use the local Real environment to claim production readiness. It is a development/qualification runtime.
