# Contributing

## Versioning

Product versions use `stable.major.minor.patch` as defined by the master plan.
The canonical working version is stored in `VERSION` and mirrored by build files.

## Commit discipline

Use focused commits. A version milestone is tagged only after its documented gates
are verified. Do not tag a release if mandatory tests or security gates were not
executed successfully.

Recommended prefixes: `feat`, `fix`, `test`, `docs`, `build`, `ci`, `refactor`,
`security`, `chore`.

## Domain boundaries

A module may expose application/domain interfaces but may not reach into another
module's repository. Provider SDKs belong in infrastructure adapters, not academic
business rules.

## Commercial feature policy

Never write business logic equivalent to `if (plan == PRO)`. Check a capability
through the licensing API, which evaluates subscription state, entitlement grant,
limits, and recorded usage.

## Review and change requirements

- Every change must pass the CI pipeline (`mvn verify` on a fresh PostgreSQL —
  which validates the full Flyway chain from zero — plus web lint/build,
  repository policy incl. the milestone qualification script, and the OSV scan).
- New tenant-facing behavior needs a tenant-isolation consideration: if it is a
  repository or query, it must be tenant-parameterized; if it is a new table, use
  composite `(tenant_id, id)` foreign keys.
- Bug fixes to security-relevant flows (assessment packets, uploads/downloads,
  webhooks, API credentials, entitlements) require a regression test in the same
  change when the test environment can express it.
- Keep the version surfaces (`VERSION`, pom.xml, application.yaml, openapi.yaml,
  package.json) in sync by running `scripts/set-version.py` for version bumps
  instead of hand-editing.
- Sign commits with `git commit -s` (DCO-style sign-off) so authorship is
  traceable; releases are tagged only from a clean, green CI run.
