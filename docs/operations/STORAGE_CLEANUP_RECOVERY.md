# Storage Cleanup Dead-Letter Recovery

## Scope and safety

The storage cleanup outbox compensates for provider objects that must be deleted after application metadata changes or a transaction rollback. Every pending row is bound to a tenant, provider, locator, object key and live quota reservation (RESERVED or COMMITTED).

The worker performs provider deletion and quota release in a transaction. A failed provider/quota operation rolls back that transaction, then records the exception category and retry time in an independent transaction. The worker does not persist or log provider exception messages, callback URLs, response bodies or credentials.

Retries use exponential backoff capped at one hour. At 12 failures, the job is dead-lettered and removed from due polling. Its quota reservation remains RESERVED or COMMITTED, exactly as it was when the job was queued. This intentionally preserves conservative capacity accounting until deletion is confirmed.

## Inspect

Use an authenticated tenant-wide operations identity with `OPERATIONS_VIEW`:

```http
GET /api/v1/storage-cleanup/dead-letter
```

The response is capped at 100 rows and exposes only the job identifier, provider, attempt count, safe exception type and dead-letter timestamp. It does not expose provider locators, object keys, or quota identifiers.

Investigate the provider-side object using deployment-approved provider tooling and the internal audit/trace context. Confirm tenant ownership and the exact object generation before taking action. Do not infer physical object identity from a display name.

## Recover

After the provider issue or quota policy issue is corrected, use a tenant-wide operations identity with `OPERATIONS_MANAGE`:

```http
POST /api/v1/storage-cleanup/{id}/requeue
```

Requeue is tenant-scoped, row-locked and audited as `STORAGE_CLEANUP_REQUEUED`. It resets the attempt counter and makes the existing job immediately eligible. The worker will still require a valid RESERVED or COMMITTED quota reservation, an enabled provider deletion operation budget, and successful provider deletion before it releases the reservation.

A branch-scoped operations identity is deliberately denied: cleanup rows currently have no branch attribution, so filtering by a branch would be unsafe.

## Do not

- Do not manually release the quota reservation for a dead-lettered job.
- Do not insert or update outbox rows directly in production.
- Do not requeue before confirming the underlying provider failure is remediated.
- Do not enable a provider, change a quota, provision paid resources, or deploy a new environment as part of recovery without separate authorization.
- Do not interpret a successful enqueue or requeue response as proof that the external object has been deleted. Verify the job subsequently reaches `deleted_at` and the reservation reaches `RELEASED`.

## Identity invariants

An active cleanup locator can be enqueued again only with the same object key and the same quota reservation. A quota reservation cannot be attached to two pending cleanup rows. Reusing a locator for a new object generation is permitted only after the earlier row is marked deleted and the new object has a new live quota reservation (RESERVED or COMMITTED). Conflicting identities are rejected rather than merged.

Worker claims are committed independently with a five-minute lease before provider I/O. A second instance cannot claim the same live lease. Successful deletion or persisted backoff clears the lease; if a worker process exits before either transition, the row becomes claimable after the lease expires. Provider deletion is therefore an at-least-once side effect and storage adapters must treat deleting an already-absent object as success.
