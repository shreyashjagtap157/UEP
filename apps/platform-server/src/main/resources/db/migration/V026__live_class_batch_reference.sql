-- Keep the JPA LiveClass batch scope consistent with the class session.
-- Live classes can only be created for batch-backed class sessions (see LiveClassService).
ALTER TABLE live_class
    ADD COLUMN batch_id uuid;

UPDATE live_class lc
SET batch_id = cs.batch_id
FROM class_session cs
WHERE cs.tenant_id = lc.tenant_id
  AND cs.id = lc.class_session_id;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM live_class WHERE batch_id IS NULL) THEN
        RAISE EXCEPTION 'Cannot add live_class.batch_id: existing live classes must reference batch-backed class sessions';
    END IF;
END
$$;

ALTER TABLE live_class
    ALTER COLUMN batch_id SET NOT NULL;

ALTER TABLE live_class
    ADD CONSTRAINT fk_live_class_batch
    FOREIGN KEY (tenant_id, batch_id)
    REFERENCES batch (tenant_id, id)
    ON DELETE RESTRICT;

CREATE INDEX ix_live_class_batch_status
    ON live_class (tenant_id, batch_id, status);
