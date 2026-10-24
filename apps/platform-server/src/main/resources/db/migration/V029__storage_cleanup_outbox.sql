-- Durable compensation for remote storage objects created by a transaction that later rolls back.
-- Provider delete operations are retried until the object is removed or the job is terminal.
CREATE TABLE storage_cleanup_outbox (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    provider varchar(32) NOT NULL,
    locator varchar(2048) NOT NULL,
    object_key varchar(1024) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_attempt_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    deleted_at timestamptz,
    last_error varchar(240),
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_storage_cleanup_object UNIQUE (tenant_id, provider, locator)
);

CREATE INDEX ix_storage_cleanup_due
    ON storage_cleanup_outbox(next_attempt_at)
    WHERE deleted_at IS NULL;
