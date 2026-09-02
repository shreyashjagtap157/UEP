# 0.15.0.0 Reliability and Performance Qualification

This milestone is a hardening release; it does not introduce a major business feature.

## Automated gates

- `bash scripts/verify-repository.sh` — repository structure, permission/event parity, secret heuristics, Git diff checks.
- `python3 scripts/qualify-0.15.py` — reliability configuration and tenant-isolation static checks.
- `python3 scripts/load-smoke.py URL REQUESTS CONCURRENCY` — dependency-free HTTP smoke/load probe.
- `k6 run scripts/load-smoke.k6.js` — repeatable service-level load profile when k6 is installed.
- `scripts/backup-restore-drill.sh` — custom-format PostgreSQL backup/restore verification.

## Performance baseline

Targets for production qualification are p95 HTTP latency below 750 ms for health/control-plane smoke traffic and p99 below 1500 ms under the included 20 requests/second arrival profile. These are qualification targets, not claims about an unrun environment.

## Reliability controls

The server configures bounded Hikari connection acquisition, validation and lifetime. Migration V023 adds tenant-leading indexes for high-frequency operational, notification, session, recording and enrollment queries.

## Security and isolation

Existing tenant-isolation integration tests remain mandatory. The 0.15 gate additionally scans for response-level credential exposure and development-only production defaults. Any warning must be reviewed before declaring a production candidate.

## Restore drill

A production-like PostgreSQL instance must be backed up with `pg_dump --format=custom`, restored into a clean database, and checked for tenant-row availability before the release can progress to 0.16.
