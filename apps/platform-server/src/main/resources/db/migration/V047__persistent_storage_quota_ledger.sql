-- Persistent storage accounting. Daily usage_counter rows are intentionally not used for byte capacity.
CREATE TABLE storage_provider_quota (
    provider varchar(32) PRIMARY KEY,
    reads_enabled boolean NOT NULL,
    writes_enabled boolean NOT NULL,
    hard_limit_bytes bigint,
    max_object_bytes bigint,
    reserved_bytes bigint NOT NULL DEFAULT 0 CHECK (reserved_bytes >= 0),
    committed_bytes bigint NOT NULL DEFAULT 0 CHECK (committed_bytes >= 0),
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_storage_provider_limit CHECK (hard_limit_bytes IS NULL OR hard_limit_bytes >= 0),
    CONSTRAINT ck_storage_provider_object_limit CHECK (max_object_bytes IS NULL OR max_object_bytes >= 0)
);

-- One configured account/bucket per provider adapter in this deployment. R2 is deliberately
-- disabled: these conservative byte limits are not a provider billing cap and must not be
-- enabled until the external account, credentials, operation budgets and trust boundary qualify.
INSERT INTO storage_provider_quota(
    provider, reads_enabled, writes_enabled, hard_limit_bytes, max_object_bytes
) VALUES
    ('LOCAL', true, true, NULL, NULL),
    ('GOOGLE_DRIVE', true, true, NULL, NULL),
    ('GOOGLE_SHARED_DRIVE', true, true, NULL, NULL),
    ('S3_COMPATIBLE', false, false, 8000000000, 1000000000);

CREATE TABLE storage_tenant_usage (
    tenant_id uuid PRIMARY KEY REFERENCES tenant(id) ON DELETE CASCADE,
    reserved_bytes bigint NOT NULL DEFAULT 0 CHECK (reserved_bytes >= 0),
    committed_bytes bigint NOT NULL DEFAULT 0 CHECK (committed_bytes >= 0),
    row_version bigint NOT NULL DEFAULT 0
);

CREATE TABLE storage_quota_reservation (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    provider varchar(32) NOT NULL REFERENCES storage_provider_quota(provider) ON DELETE RESTRICT,
    reservation_key varchar(200) NOT NULL,
    state varchar(16) NOT NULL CHECK (state IN ('RESERVED', 'COMMITTED', 'RELEASED')),
    reserved_bytes bigint NOT NULL DEFAULT 0 CHECK (reserved_bytes >= 0),
    committed_bytes bigint NOT NULL DEFAULT 0 CHECK (committed_bytes >= 0),
    object_locator varchar(2048),
    object_key varchar(1024),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at timestamptz,
    CONSTRAINT uq_storage_reservation_key UNIQUE (tenant_id, reservation_key),
    CONSTRAINT ck_storage_reservation_state CHECK (
        (state = 'RESERVED' AND committed_bytes = 0 AND released_at IS NULL)
        OR (state = 'COMMITTED' AND reserved_bytes = 0 AND object_locator IS NOT NULL AND released_at IS NULL)
        OR (state = 'RELEASED' AND reserved_bytes = 0 AND committed_bytes = 0 AND released_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_storage_reservation_live_object
    ON storage_quota_reservation(tenant_id, provider, object_locator)
    WHERE state = 'COMMITTED' AND object_locator IS NOT NULL;
CREATE INDEX ix_storage_reservation_provider_state
    ON storage_quota_reservation(provider, state);
CREATE INDEX ix_storage_reservation_tenant_state
    ON storage_quota_reservation(tenant_id, state);

ALTER TABLE upload_session
    ADD COLUMN quota_reservation_id uuid;
ALTER TABLE storage_object
    ADD COLUMN quota_reservation_id uuid;
ALTER TABLE recording
    ADD COLUMN quota_reservation_id uuid;
ALTER TABLE recording_asset
    ADD COLUMN quota_reservation_id uuid;
ALTER TABLE storage_cleanup_outbox
    ADD COLUMN quota_reservation_id uuid;

-- Existing metadata must agree on the size of any shared provider locator. Stop the migration
-- rather than fabricating capacity when two rows disagree.
DO $$
DECLARE
    inconsistent boolean;
    missing_recording_size boolean;
BEGIN
    WITH legacy_object AS (
        SELECT tenant_id, provider, locator, size_bytes
        FROM storage_object
        WHERE status <> 'DELETED'
        UNION ALL
        SELECT tenant_id, provider, locator, size_bytes
        FROM recording_asset
        UNION ALL
        SELECT tenant_id, storage_provider, storage_locator, size_bytes
        FROM recording
        WHERE storage_locator IS NOT NULL
          AND size_bytes IS NOT NULL
          AND status IN ('READY', 'ARCHIVING', 'ARCHIVED')
    )
    SELECT EXISTS (
        SELECT 1
        FROM legacy_object
        GROUP BY tenant_id, provider, locator
        HAVING MIN(size_bytes) <> MAX(size_bytes)
    ) INTO inconsistent;

    IF inconsistent THEN
        RAISE EXCEPTION 'Storage quota backfill stopped: one provider locator has conflicting byte sizes';
    END IF;

    SELECT EXISTS (
        SELECT 1 FROM recording
        WHERE storage_locator IS NOT NULL
          AND size_bytes IS NULL
          AND status IN ('READY', 'ARCHIVING', 'ARCHIVED')
    ) INTO missing_recording_size;

    IF missing_recording_size THEN
        RAISE EXCEPTION 'Storage quota backfill stopped: a live recording has a locator but no byte size';
    END IF;
END $$;

-- Backfill one committed reservation per distinct physical object. A learning resource version,
-- recording row and MASTER asset may all reference the same object; it is counted exactly once.
WITH legacy_object AS (
    SELECT tenant_id, provider, locator, size_bytes, object_key
    FROM storage_object
    WHERE status <> 'DELETED'
    UNION ALL
    SELECT tenant_id, provider, locator, size_bytes, object_key
    FROM recording_asset
    UNION ALL
    SELECT tenant_id, storage_provider, storage_locator, size_bytes, object_key
    FROM recording
    WHERE storage_locator IS NOT NULL
      AND size_bytes IS NOT NULL
      AND status IN ('READY', 'ARCHIVING', 'ARCHIVED')
),
physical_object AS (
    SELECT tenant_id, provider, locator, MAX(size_bytes) AS size_bytes, MIN(object_key) AS object_key
    FROM legacy_object
    GROUP BY tenant_id, provider, locator
)
INSERT INTO storage_quota_reservation(
    id, tenant_id, provider, reservation_key, state, reserved_bytes, committed_bytes,
    object_locator, object_key, created_at, updated_at
)
SELECT uuidv7(), tenant_id, provider,
       'legacy:' || md5(tenant_id::text || ':' || provider || ':' || locator),
       'COMMITTED', 0, size_bytes, locator, object_key, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM physical_object;

UPDATE storage_object object
SET quota_reservation_id = reservation.id
FROM storage_quota_reservation reservation
WHERE reservation.tenant_id = object.tenant_id
  AND reservation.provider = object.provider
  AND reservation.object_locator = object.locator
  AND reservation.state = 'COMMITTED';

UPDATE recording_asset asset
SET quota_reservation_id = reservation.id
FROM storage_quota_reservation reservation
WHERE reservation.tenant_id = asset.tenant_id
  AND reservation.provider = asset.provider
  AND reservation.object_locator = asset.locator
  AND reservation.state = 'COMMITTED';

UPDATE recording recording
SET quota_reservation_id = reservation.id
FROM storage_quota_reservation reservation
WHERE reservation.tenant_id = recording.tenant_id
  AND reservation.provider = recording.storage_provider
  AND reservation.object_locator = recording.storage_locator
  AND reservation.state = 'COMMITTED'
  AND recording.storage_locator IS NOT NULL;

-- Preserve capacity for uploads that were still active at migration time. Completed sessions
-- reuse the committed storage-object reservation; terminal sessions do not reserve capacity.
INSERT INTO storage_quota_reservation(
    id, tenant_id, provider, reservation_key, state, reserved_bytes, committed_bytes,
    created_at, updated_at
)
SELECT uuidv7(), tenant_id, provider, 'upload:' || id::text, 'RESERVED',
       expected_bytes, 0, created_at, CURRENT_TIMESTAMP
FROM upload_session
WHERE status IN ('INITIATED', 'UPLOADING', 'VERIFYING')
  AND expires_at > CURRENT_TIMESTAMP;

UPDATE upload_session upload
SET quota_reservation_id = reservation.id
FROM storage_quota_reservation reservation
WHERE reservation.tenant_id = upload.tenant_id
  AND reservation.reservation_key = 'upload:' || upload.id::text;

UPDATE upload_session upload
SET quota_reservation_id = object.quota_reservation_id
FROM storage_object object
WHERE upload.tenant_id = object.tenant_id
  AND upload.storage_object_id = object.id
  AND upload.status = 'COMPLETED';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM upload_session
        WHERE status = 'COMPLETED' AND quota_reservation_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Storage quota backfill stopped: a completed upload has no committed storage object';
    END IF;
END $$;

INSERT INTO storage_tenant_usage(tenant_id, reserved_bytes, committed_bytes)
SELECT tenant_id, SUM(reserved_bytes), SUM(committed_bytes)
FROM storage_quota_reservation
WHERE state IN ('RESERVED', 'COMMITTED')
GROUP BY tenant_id;

UPDATE storage_provider_quota quota
SET reserved_bytes = usage.reserved_bytes,
    committed_bytes = usage.committed_bytes,
    updated_at = CURRENT_TIMESTAMP
FROM (
    SELECT provider,
           SUM(reserved_bytes) AS reserved_bytes,
           SUM(committed_bytes) AS committed_bytes
    FROM storage_quota_reservation
    WHERE state IN ('RESERVED', 'COMMITTED')
    GROUP BY provider
) usage
WHERE quota.provider = usage.provider;

ALTER TABLE upload_session
    ADD CONSTRAINT fk_upload_quota_reservation
    FOREIGN KEY (quota_reservation_id) REFERENCES storage_quota_reservation(id) ON DELETE RESTRICT;
ALTER TABLE storage_object
    ADD CONSTRAINT fk_storage_object_quota_reservation
    FOREIGN KEY (quota_reservation_id) REFERENCES storage_quota_reservation(id) ON DELETE RESTRICT;
ALTER TABLE recording
    ADD CONSTRAINT fk_recording_quota_reservation
    FOREIGN KEY (quota_reservation_id) REFERENCES storage_quota_reservation(id) ON DELETE RESTRICT;
ALTER TABLE recording_asset
    ADD CONSTRAINT fk_recording_asset_quota_reservation
    FOREIGN KEY (quota_reservation_id) REFERENCES storage_quota_reservation(id) ON DELETE RESTRICT;
ALTER TABLE storage_cleanup_outbox
    ADD CONSTRAINT fk_storage_cleanup_quota_reservation
    FOREIGN KEY (quota_reservation_id) REFERENCES storage_quota_reservation(id) ON DELETE RESTRICT;
