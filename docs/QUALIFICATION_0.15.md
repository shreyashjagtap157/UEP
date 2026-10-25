# 0.15.0.0 Reliability and Performance Qualification

This milestone is a hardening release; it does not introduce a major business feature.

## Automated gates

- `bash scripts/verify-repository.sh` — repository structure, permission/event parity, secret heuristics, Git diff checks.
- `python3 scripts/qualify-0.15.py` — reliability configuration, tenant-isolation, and Flyway migration-version uniqueness checks.
- `python3 scripts/load-smoke.py URL REQUESTS CONCURRENCY` — dependency-free HTTP smoke/load probe.
- Backend CI starts the packaged server against a fresh PostgreSQL database and runs 30 concurrent health requests with conservative p95/p99 smoke bounds. This validates startup/migration/runtime wiring; it is not the production load target.
- Most recent verified CI observation (run #297): 118 backend tests passed with zero failures/errors; Flyway validated all 32 migrations; the packaged server booted against a fresh database; and 30/30 concurrent health requests passed at 6-worker concurrency with p95 18.1 ms and p99 20.0 ms. Web lint passed with 2 warnings and 0 errors, all 21 web demo tests and the production build passed, and repository policy/qualification plus dependency scanning passed. These measurements describe the GitHub runner/container only and must not be used as production SLO evidence.
- `k6 run scripts/load-smoke.k6.js` — repeatable service-level load profile when k6 is installed.
- `scripts/backup-restore-drill.sh` — custom-format PostgreSQL backup/restore verification.

## Performance baseline

Targets for production qualification are p95 HTTP latency below 750 ms for health/control-plane smoke traffic and p99 below 1500 ms under the included 20 requests/second arrival profile. These are qualification targets, not claims about an unrun environment.

## Reliability controls

The server configures bounded Hikari connection acquisition, validation and lifetime. Migration V023 adds tenant-leading indexes for high-frequency operational, notification, session, recording and enrollment queries.

The `MANUAL` finance provider is explicitly an operator-attested settlement workflow, not an online payment gateway. Recording a payment requires a settlement reference and explicit confirmation that funds have settled; reference reuse is blocked by a tenant/provider database uniqueness index. The provider does not report manual refunds as successful. A production gateway still requires real authorization/capture, signed webhooks, idempotency and reconciliation tests. Provider-declared authorization/capture declines are represented by a committed `FAILED` payment row while the invoice and receipts remain unchanged; a controlled `502` response reports the provider failure without exposing provider-specific details. The finance failure-state regression test verifies both failure branches and the transaction's no-rollback policy. This covers explicit declines; network timeouts after a provider accepted a capture remain ambiguous and require a real gateway's idempotency and reconciliation workflow. Payment creation also locks the current tenant’s invoice row before checking the remaining balance. The concurrent invoice payment integration test races two 60-unit settlements against a 100-unit invoice and asserts that only one is captured, paid, and receipted.

Malware-scanner rejection now commits the terminal `ABORTED` upload state before returning the conflict response, so a rejected session cannot remain `UPLOADING` after its staging file has been deleted. The S3-compatible adapter configures 5-second connection timeout, 5-minute socket timeout, 10-minute attempt timeout, and 30-minute whole-call timeout, and closes its SDK client at shutdown. S3 startup also rejects non-HTTP(S) endpoints, blank buckets, and partially configured static credential pairs (it permits both credentials blank only when explicitly relying on the AWS default credential chain). Google Drive multipart upload is bounded at 30 minutes to avoid a short timeout on large content. These long upper bounds accommodate the configured multi-gigabyte upload limit; they are not latency SLOs. Upload completion registers transaction-completion compensation for the remote object: if metadata/audit persistence rolls back, the provider object is deleted immediately, and a durable `storage_cleanup_outbox` job is created if compensation fails. Successful commits remove the local staging file after commit; the upload cleanup worker retries failed staging-file deletion and persists a `staging_cleaned_at` marker so already-cleaned sessions do not starve newer work in fixed-size batches. The CI unit tests exercise commit, rollback, provider-delete failure, and audit-failure paths. This protects handled transaction failures; a process crash in the narrow interval between provider acceptance and registering the completion callback remains a residual risk that should be covered by provider reconciliation/production restore drills.

## Security and isolation

Existing tenant-isolation integration tests remain mandatory. The 0.15 gate additionally scans for response-level credential exposure and development-only production defaults. Any warning must be reviewed before declaring a production candidate.

## Restore drill

A production-like PostgreSQL instance must be backed up with `pg_dump --format=custom`, restored into a uniquely named disposable database, and checked for tenant, membership, and successful Flyway-migration readability before the release can progress to 0.16. The script requires `RESTORE_TARGET_IS_DISPOSABLE=true`, refuses source/target database identity collisions and backup-file overwrites, uses restrictive backup-file permissions, and stops on restore errors. Never point the restore target at a production database; `pg_restore --clean` intentionally removes conflicting target objects.
