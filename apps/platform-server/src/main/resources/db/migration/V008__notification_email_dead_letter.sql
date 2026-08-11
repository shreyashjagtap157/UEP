DO $$
DECLARE
    status_constraint text;
BEGIN
    SELECT c.conname
      INTO status_constraint
      FROM pg_constraint c
     WHERE c.conrelid = 'notification_delivery'::regclass
       AND c.contype = 'c'
       AND pg_get_constraintdef(c.oid) ILIKE '%status%'
     LIMIT 1;

    IF status_constraint IS NOT NULL THEN
        EXECUTE format('ALTER TABLE notification_delivery DROP CONSTRAINT %I', status_constraint);
    END IF;
END $$;

ALTER TABLE notification_delivery
    ADD CONSTRAINT ck_notification_delivery_status
    CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED', 'DEAD_LETTERED'));
