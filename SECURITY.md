# Security Policy

## Reporting

Do **not** disclose suspected vulnerabilities publicly. Report them via
GitHub **Private Vulnerability Reporting** (Security tab of this repository)
or by contacting the repository owner directly. Operators of a deployed
instance must provide their own incident channel to their institution
customers; this repository governs the product source.

- Please include: affected version (`VERSION`), affected endpoint/module,
  reproduction steps, and impact assessment.
- You will receive an initial response within 7 days.
- Accepted reports are disclosed publicly only after a fix ships; reporters
  are credited on request.

## Baseline security properties

- No cross-tenant data access is permitted. The database enforces composite
  tenant foreign keys; the application resolves tenant context exclusively
  from trusted authentication claims (never from client-provided headers or
  bodies).
- Authentication tokens, provider credentials, and private license material
  must never be committed to source control or written to logs.
- Development-secret defaults (`change-me-in-production`, `devkey`) are
  blocked at boot by the platform configuration guard; production must
  provide explicit secrets.
- Authorization is enforced by the server even when a UI hides an action.
- Assessment answer keys are never released to learner clients; exam packets
  are served redacted and audience-checked.
- Entitlement decisions are server-authoritative.
- Uploaded customer content is treated as untrusted: length and optional
  SHA-256 verification, optional malware scanning, traversal-safe local
  storage, and download authorization on every request.
- Database migrations and audit history are append/revision oriented where
  business history must be preserved.

## Dependency handling

- CI runs `mvn verify` against a fresh PostgreSQL instance (which also
  validates the complete Flyway migration chain from zero) and an OSV
  dependency scan; Dependabot keeps Maven/npm/Actions patches current.
- Production releases require dependency/SBOM generation and vulnerability
  review. Third-party licenses must be preserved in distribution notices.
