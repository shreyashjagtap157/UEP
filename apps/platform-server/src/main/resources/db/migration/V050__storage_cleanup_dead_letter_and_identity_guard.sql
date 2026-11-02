-- Bound the retry lifetime of provider-object cleanup while retaining quota reservations
-- until an operator deliberately replays a terminal job.
ALTER TABLE storage_cleanup_outbox
    ADD COLUMN dead_lettered_at timestamptz,
    ADD COLUMN processing_token uuid,
    ADD COLUMN processing_lease_expires_at timestamptz,
    ADD CONSTRAINT ck_storage_cleanup_processing_lease
        CHECK (
            (processing_token IS NULL AND processing_lease_expires_at IS NULL)
            OR (processing_token IS NOT NULL AND processing_lease_expires_at IS NOT NULL)
        );

-- Existing jobs could retry forever before this migration. Preserve their failure evidence
-- and move already-exhausted jobs out of the due queue without releasing quota.
UPDATE storage_cleanup_outbox
SET dead_lettered_at = next_attempt_at
WHERE deleted_at IS NULL
  AND attempt_count >= 12;

ALTER TABLE storage_cleanup_outbox
    ADD CONSTRAINT ck_storage_cleanup_terminal_state
        CHECK (deleted_at IS NULL OR dead_lettered_at IS NULL),
    ADD CONSTRAINT ck_storage_cleanup_dead_letter_attempts
        CHECK (dead_lettered_at IS NULL OR attempt_count >= 12);

DROP INDEX IF EXISTS ix_storage_cleanup_due;

CREATE INDEX ix_storage_cleanup_due
    ON storage_cleanup_outbox(next_attempt_at, created_at, id)
    WHERE deleted_at IS NULL AND dead_lettered_at IS NULL;

CREATE INDEX ix_storage_cleanup_dead_lettered
    ON storage_cleanup_outbox(tenant_id, dead_lettered_at, id)
    WHERE deleted_at IS NULL AND dead_lettered_at IS NOT NULL;

CREATE INDEX ix_storage_cleanup_expired_lease
    ON storage_cleanup_outbox(processing_lease_expires_at, id)
    WHERE deleted_at IS NULL AND dead_lettered_at IS NULL AND processing_lease_expires_at IS NOT NULL;

DO $storage_cleanup_reservation_guard$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM storage_cleanup_outbox
        WHERE deleted_at IS NULL AND quota_reservation_id IS NOT NULL
        GROUP BY tenant_id, provider, quota_reservation_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Storage cleanup migration blocked: one live quota reservation is attached to multiple pending cleanup jobs';
    END IF;
END
$storage_cleanup_reservation_guard$;

CREATE UNIQUE INDEX uq_storage_cleanup_pending_reservation
    ON storage_cleanup_outbox(tenant_id, provider, quota_reservation_id)
    WHERE deleted_at IS NULL AND quota_reservation_id IS NOT NULL;
