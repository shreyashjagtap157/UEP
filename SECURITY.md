# Security Policy

## Reporting

Do not disclose suspected vulnerabilities publicly. Report them through the
private security contact/process designated by the repository owner for the
active deployment.

## Baseline security properties

- No cross-tenant data access is permitted.
- Authentication tokens, provider credentials, and private license material must
  never be committed to source control or written to logs.
- Authorization is enforced by the server even when a UI hides an action.
- Entitlement decisions are server-authoritative.
- Uploaded customer content is treated as untrusted.
- Database migrations and audit history are append/revision oriented where
  business history must be preserved.

## Dependency handling

Production releases require dependency/SBOM generation and vulnerability review.
Third-party licenses must be preserved in distribution notices.
