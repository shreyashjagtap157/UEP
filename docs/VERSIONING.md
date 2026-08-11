# Versioning and Git Release Discipline

The product uses four components: `stable.major.minor.patch`.

Examples:

- `0.1.0.0-SNAPSHOT` — work toward the 0.1.0.0 engineering-foundation release.
- `0.1.0.1-SNAPSHOT` — patch development after 0.1.0.0.
- `1.0.0.0` — first stable commercial release when the roadmap gates are met.

Use `scripts/set-version.py` to update all canonical version surfaces together.
A release tag `vX.Y.Z.P` is created only from a clean commit after the milestone's
full verification gates pass. Snapshot commits are not release-tagged.

Recommended sequence:

1. implement and test on `X.Y.Z.P-SNAPSHOT`;
2. run repository, backend, frontend, migration, security and deployment gates;
3. set `X.Y.Z.P`;
4. commit `chore(release): prepare X.Y.Z.P`;
5. tag that exact commit as `vX.Y.Z.P`;
6. immediately move development to the next `-SNAPSHOT` version.
