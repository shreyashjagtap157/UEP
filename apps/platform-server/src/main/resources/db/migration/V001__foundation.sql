CREATE TABLE tenant (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    slug varchar(80) NOT NULL UNIQUE,
    display_name varchar(200) NOT NULL,
    status varchar(32) NOT NULL CHECK (status IN ('ACTIVE', 'READ_ONLY', 'SUSPENDED', 'CLOSED')),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0
);

CREATE TABLE subscription (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL UNIQUE REFERENCES tenant(id) ON DELETE RESTRICT,
    status varchar(32) NOT NULL CHECK (status IN ('TRIAL', 'ACTIVE', 'GRACE', 'SUSPENDED', 'EXPIRED', 'REVOKED', 'TERMINATED')),
    starts_at timestamptz NOT NULL,
    grace_ends_at timestamptz,
    expires_at timestamptz,
    version bigint NOT NULL DEFAULT 0
);

CREATE TABLE entitlement_grant (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    feature_key varchar(64) NOT NULL,
    enabled boolean NOT NULL,
    valid_from timestamptz,
    valid_until timestamptz,
    CONSTRAINT uq_entitlement_feature UNIQUE (tenant_id, feature_key),
    CONSTRAINT ck_entitlement_window CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until > valid_from)
);

CREATE TABLE entitlement_limit (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    limit_key varchar(64) NOT NULL,
    hard_limit bigint NOT NULL CHECK (hard_limit >= 0),
    CONSTRAINT uq_entitlement_limit UNIQUE (tenant_id, limit_key)
);

CREATE TABLE usage_counter (
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    limit_key varchar(64) NOT NULL,
    period_start timestamptz NOT NULL,
    period_end timestamptz NOT NULL,
    consumed bigint NOT NULL DEFAULT 0 CHECK (consumed >= 0),
    version bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, limit_key, period_start),
    CONSTRAINT ck_usage_period CHECK (period_end > period_start)
);

CREATE TABLE audit_event (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    actor_subject varchar(160) NOT NULL,
    action varchar(120) NOT NULL,
    resource_type varchar(120) NOT NULL,
    resource_id varchar(160),
    occurred_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_entitlement_grant_tenant ON entitlement_grant(tenant_id);
CREATE INDEX ix_usage_counter_tenant_period ON usage_counter(tenant_id, period_start DESC);
CREATE INDEX ix_audit_event_tenant_time ON audit_event(tenant_id, occurred_at DESC);
