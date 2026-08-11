ALTER TABLE subscription
    ADD COLUMN authority_kind varchar(32) NOT NULL DEFAULT 'PLATFORM_MANAGED',
    ADD COLUMN external_license_id varchar(120),
    ADD COLUMN license_revision bigint NOT NULL DEFAULT 0;

ALTER TABLE subscription
    ADD CONSTRAINT ck_subscription_authority_kind
    CHECK (authority_kind IN ('PLATFORM_MANAGED', 'SIGNED_OFFLINE_MANIFEST'));

CREATE UNIQUE INDEX uq_subscription_external_license_id
    ON subscription(external_license_id)
    WHERE external_license_id IS NOT NULL;
