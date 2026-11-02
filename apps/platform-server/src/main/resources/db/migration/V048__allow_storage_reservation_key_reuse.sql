-- A released reservation is historical evidence, not an active idempotency lock.
-- Retries of recordings and provider migrations must be able to create a fresh reservation
-- with the same logical operation key while preserving the released ledger row.
ALTER TABLE storage_quota_reservation
    DROP CONSTRAINT uq_storage_reservation_key;

CREATE UNIQUE INDEX uq_storage_reservation_active_key
    ON storage_quota_reservation(tenant_id, reservation_key)
    WHERE state IN ('RESERVED', 'COMMITTED');
