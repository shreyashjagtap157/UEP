-- Align the tenant control-plane entity with the authoritative tenant table.
-- Existing tenants receive conservative defaults; no existing tenant rows are deleted.
ALTER TABLE tenant
    ADD COLUMN deployment_platform varchar(32) NOT NULL DEFAULT 'fly.io',
    ADD COLUMN deployment_status varchar(32) NOT NULL DEFAULT 'pending',
    ADD COLUMN licensed_user_count integer NOT NULL DEFAULT 0,
    ADD COLUMN max_licensed_users integer NOT NULL DEFAULT 100,
    ADD COLUMN onboarding_date timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN last_activity_at timestamptz,
    ADD COLUMN subscription_tier varchar(32) NOT NULL DEFAULT 'free',
    ADD COLUMN custom_domain varchar(255);

ALTER TABLE tenant
    ADD CONSTRAINT ck_tenant_licensed_user_counts
    CHECK (licensed_user_count >= 0 AND max_licensed_users >= 0);

UPDATE tenant
SET last_activity_at = created_at
WHERE last_activity_at IS NULL;

CREATE INDEX ix_tenant_deployment_status
    ON tenant (deployment_platform, deployment_status);

CREATE INDEX ix_tenant_last_activity
    ON tenant (last_activity_at)
    WHERE last_activity_at IS NOT NULL;
