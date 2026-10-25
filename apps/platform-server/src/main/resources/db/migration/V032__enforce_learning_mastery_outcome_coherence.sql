-- Keep a competency's owning outcome coherent with every mastery record.
-- Reconcile older inconsistent rows before installing the composite foreign key.
UPDATE competency_mastery AS cm
SET outcome_id = c.outcome_id
FROM competency AS c
WHERE cm.tenant_id = c.tenant_id
  AND cm.competency_id = c.id
  AND cm.outcome_id <> c.outcome_id;

ALTER TABLE competency
    ADD CONSTRAINT uq_competency_tenant_outcome_id
    UNIQUE (tenant_id, outcome_id, id);

ALTER TABLE competency_mastery
    ADD CONSTRAINT fk_mastery_competency_outcome
    FOREIGN KEY (tenant_id, outcome_id, competency_id)
    REFERENCES competency (tenant_id, outcome_id, id)
    ON DELETE CASCADE;
