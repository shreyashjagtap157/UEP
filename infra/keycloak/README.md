# Local Keycloak OIDC development

This folder contains two different realm exports:

- `education-realm.json` is the **local development realm** used by the native Windows launcher and the optional `infra/compose.yaml` Keycloak service.
- `education-realm-staging.json` is the staging-oriented export used only by the optimized Keycloak container build. It is not the local developer realm and must not be copied over it.

The local Keycloak path is optional. The default UEP launcher remains `LocalNative`, which does not start or contact Keycloak.

## Recommended native Windows setup

Run from the repository root:

```powershell
.\startuep.ps1 -Mode Real -Database SQLite -IdentityProvider Keycloak
```

For a paired Real + Offline Demo presentation:

```powershell
.\startuep.ps1 -Mode Presentation -Database SQLite -IdentityProvider Keycloak
```

The first run:

1. Checks the standard UEP prerequisites (Node.js 22.22+, JDK 21+, Maven 3.9+).
2. Installs or locates a separate full JDK 25+ for Keycloak. The backend remains compiled with `maven.compiler.release=21`; the Keycloak runtime does not change the backend bytecode baseline.
3. Downloads the official Keycloak 26.8.2 Windows ZIP to the ignored `.uep-local/tools` directory and verifies the pinned SHA-256 before extraction.
4. Copies this directory's `education-realm.json` to Keycloak's `data/import` directory.
5. Generates separate random Keycloak administrator and UEP owner passwords, stores them in the ACL-protected LocalAppData UEP state, and supplies them to the Keycloak process through environment variables (not command-line arguments or committed realm secrets).
6. Starts Keycloak on loopback port 8081, verifies the realm OIDC discovery issuer, then starts Spring Boot with `local-keycloak` and Vite with the real OIDC client settings.

The Keycloak server is bound to `127.0.0.1:8081`. The UEP API remains on `127.0.0.1:8080`; the Real frontend uses `localhost:5173`. The Offline Demo, when requested, remains a separate fixture-backed frontend on `localhost:5174`.

### Local accounts and credentials

The imported realm contains one local owner:

- Realm: `education`
- Client: `platform-web` (public browser client)
- Username: `uep-owner`
- Email: `owner@local.uep`
- UEP tenant: `uep-keycloak-local`
- UEP role: `Organization Owner`

The realm's `tenant_id` user attribute is mapped into the ID/access/userinfo tokens. The backend bootstraps the matching local tenant, OIDC-subject user, active membership and Organization Owner assignment. A mismatched or inactive reserved tenant is rejected rather than silently remapped or reactivated.

To display the generated administrator and owner passwords intentionally, open a **separate** PowerShell window and run:

```powershell
.\scripts\Show-UEP-LocalKeycloakCredentials.ps1
```

The values are stored in:

```
%LOCALAPPDATA%\UEP\local-native\state.json
```

Windows ACLs restrict the state directory and file to the current user. The JSON state is **not DPAPI-encrypted**; protect the Windows account and device, do not copy the state file, and do not paste the passwords into logs, chat, source control or screenshots. The credential helper prints passwords only when explicitly launched.

The Keycloak dev-file store is under:

```
.uep-local\tools\keycloak-26.8.2\data
```

It is independent from `.uep-local\uep-local.sqlite` or the local PostgreSQL database. Stopping the launcher stops Keycloak, Spring Boot and Vite processes but preserves both stores.

## OIDC and security boundaries

The local `platform-web` client uses Authorization Code flow with PKCE S256. Implicit flow and direct/password grants are disabled. Redirect origins are limited to the two local Real frontend hostnames. The owner password is supplied through `UEP_LOCAL_KEYCLOAK_OWNER_PASSWORD` as a realm-import placeholder; it is not committed.

The Spring `local-keycloak` profile enables normal resource-server JWT validation against the exact local issuer and JWKS endpoint. The local JWT tenant filter reads the mapped `tenant_id` claim. The profile binds Spring Boot to loopback and is mutually exclusive with `local-native`, `prod` and `production`.

This is **local development only**:

- Keycloak runs in development mode over HTTP and has a file-based store.
- Its generated passwords and local issuer are not production configuration.
- Do not expose port 8081 to a LAN, public network, reverse proxy or hosted deployment.
- Do not reuse the generated users, passwords, tenant UUID or realm data as production bootstrap.
- A green local OIDC browser test does not qualify the real hosted identity provider, HTTPS deployment, MFA policy or production recovery.

## Realm import behavior and safe updates

The native launcher starts Keycloak with `--import-realm`. Keycloak imports the `education` realm when it is absent; it does **not** replace an already-existing realm with a later edited JSON export. Therefore, changing `education-realm.json` in Git does not automatically update a developer's existing local Keycloak data.

For a normal first-run update, keep the existing local data and use the Keycloak Admin Console to make the desired realm change deliberately. If an intentional disposable development reset is required, export or record any local-only data that must be retained, stop UEP, then delete only the Keycloak development data directory shown above. On the next Keycloak launch the realm is imported again from the checked-in JSON. This destroys the local Keycloak users, sessions and realm-side changes. **Do not delete the UEP SQLite file or PostgreSQL database as part of a Keycloak reset.**

The current local realm is separate from the staging realm export. Do not replace `education-realm-staging.json` or the staging Docker build with this loopback-only realm.

## Optional Docker Compose service

The repository Compose service is an alternative local container path. It also uses Keycloak 26.8.2, imports this local realm, stores its dev data in the `keycloak-data` named volume, and binds the host port only to 127.0.0.1.

From the repository root, supply three different random values in the current PowerShell process and start only the Keycloak service:

```powershell
$env:KC_BOOTSTRAP_ADMIN_USERNAME = 'uep-local-admin'
$env:KC_BOOTSTRAP_ADMIN_PASSWORD = Read-Host 'New local Keycloak admin password'
$env:UEP_LOCAL_KEYCLOAK_OWNER_PASSWORD = Read-Host 'Different local UEP owner password'
docker compose -f infra/compose.yaml up -d keycloak
```

The Compose interpolation is deliberately required: it fails when any of the credentials is missing. Do not put real passwords in the checked-in `.env.example`; use an untracked `.env` file or, preferably, environment variables supplied only to the local command. The Compose service uses an independent container volume; it does not share the native Windows `.uep-local/tools` dev-file store.

To stop the optional Compose service without deleting its data:

```powershell
docker compose -f infra/compose.yaml stop keycloak
```

Do not add `-v` unless you intentionally want to destroy the Compose Keycloak volume and have verified that it contains no data to keep.

## Verification

The repository provides two separate native browser checks:

- `apps/web/test/native/real-sqlite.native.spec.ts`: the default LocalNative session, API persistence, SQLite file and sign-out boundary.
- `apps/web/test/native/real-keycloak.native.spec.ts`: local Keycloak discovery, Authorization Code + PKCE S256, bearer-authenticated `/api/v1/me`, and the fixed tenant/Organization Owner identity.

The Windows CI workflow runs the LocalNative smoke first, then starts the Keycloak mode separately and runs the OIDC browser check. Neither test activates both identity profiles in one backend process.
