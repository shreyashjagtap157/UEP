-- Durable, independent publication/expiry retry state prevents poison records from
-- occupying the head of a fixed-size due batch forever.
ALTER TABLE announcement
    ADD COLUMN publish_attempt_count integer NOT NULL DEFAULT 0 CHECK (publish_attempt_count >= 0),
    ADD COLUMN publish_available_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN publish_dead_lettered_at timestamptz,
    ADD COLUMN publish_last_error varchar(160),
    ADD COLUMN expiry_attempt_count integer NOT NULL DEFAULT 0 CHECK (expiry_attempt_count >= 0),
    ADD COLUMN expiry_available_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN expiry_dead_lettered_at timestamptz,
    ADD COLUMN expiry_last_error varchar(160);

CREATE INDEX ix_announcement_publish_worker_due
    ON announcement(publish_available_at, publish_at, id)
    WHERE status = 'SCHEDULED' AND publish_dead_lettered_at IS NULL;

CREATE INDEX ix_announcement_expiry_worker_due
    ON announcement(expiry_available_at, expires_at, id)
    WHERE status = 'PUBLISHED' AND expiry_dead_lettered_at IS NULL;
