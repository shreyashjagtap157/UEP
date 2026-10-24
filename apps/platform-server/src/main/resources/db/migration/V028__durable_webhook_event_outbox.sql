-- Durable webhook fan-out is part of the source event transaction. Actual HTTP delivery
-- happens later from webhook_delivery, using the existing retry worker.
CREATE TABLE webhook_event_outbox (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    source_outbox_id uuid NOT NULL,
    aggregate_id uuid NOT NULL,
    event_type varchar(64) NOT NULL,
    title varchar(240) NOT NULL,
    body varchar(4000) NOT NULL,
    occurred_at timestamptz NOT NULL,
    available_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    processed_at timestamptz,
    dead_lettered_at timestamptz,
    last_error varchar(1000),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_webhook_event_outbox_source UNIQUE (tenant_id, source_outbox_id)
);

CREATE INDEX ix_webhook_event_outbox_due
    ON webhook_event_outbox(available_at)
    WHERE processed_at IS NULL AND dead_lettered_at IS NULL;
