-- V025: upload finalize idempotency + retention lifecycle metadata.
-- storage_object_id: link from a completed upload session back to the storage object it produced,
-- so retrying POST /uploads/{id}/complete returns the existing object instead of creating a duplicate.
-- purged_at: timestamp set by ContentRetentionWorker once all of a resource's version storage
-- objects have been handled, so the retention poller stops re-scanning finished resources
-- (findTop100ByExpiresAtBefore had a head-of-line stall on the oldest fully-processed rows).
ALTER TABLE upload_session ADD COLUMN storage_object_id uuid;
ALTER TABLE learning_resource ADD COLUMN purged_at timestamptz;
