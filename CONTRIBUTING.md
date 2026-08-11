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
