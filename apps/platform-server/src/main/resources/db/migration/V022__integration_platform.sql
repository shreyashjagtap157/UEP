CREATE TABLE api_credential (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    membership_id uuid NOT NULL,
    name varchar(120) NOT NULL,
    key_hash varchar(128) NOT NULL,
    scopes varchar(2000) NOT NULL DEFAULT '',
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at timestamptz,
    expires_at timestamptz,
    revoked_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_api_credential_hash UNIQUE (key_hash),
    CONSTRAINT uq_api_credential_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_api_credential_membership FOREIGN KEY (tenant_id, membership_id)
        REFERENCES tenant_membership(tenant_id, id) ON DELETE CASCADE
);
CREATE INDEX ix_api_credential_member ON api_credential(tenant_id, membership_id, created_at DESC);

CREATE TABLE webhook_subscription (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    callback_url varchar(1000) NOT NULL,
    event_filter varchar(120) NOT NULL DEFAULT '*',
    secret_ciphertext varchar(500) NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_delivered_at timestamptz,
    last_failed_at timestamptz,
    last_error varchar(1000),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_webhook_tenant_url UNIQUE (tenant_id, callback_url),
    CONSTRAINT uq_webhook_tenant_id UNIQUE (tenant_id, id)
);
CREATE INDEX ix_webhook_subscription_enabled ON webhook_subscription(tenant_id, enabled);

CREATE TABLE webhook_delivery (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    subscription_id uuid NOT NULL,
    event_id uuid NOT NULL,
    event_type varchar(64) NOT NULL,
    payload_json text NOT NULL,
    attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at timestamptz,
    last_error varchar(1000),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_webhook_event_subscription UNIQUE (subscription_id, event_id),
    CONSTRAINT fk_webhook_delivery_subscription FOREIGN KEY (tenant_id, subscription_id)
        REFERENCES webhook_subscription(tenant_id, id) ON DELETE CASCADE
);
CREATE INDEX ix_webhook_delivery_due ON webhook_delivery(next_attempt_at) WHERE delivered_at IS NULL AND attempt_count < 12;

CREATE TABLE identity_provider (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    provider_key varchar(64) NOT NULL,
    issuer varchar(400) NOT NULL,
    authorization_endpoint varchar(500) NOT NULL,
    client_id varchar(200) NOT NULL,
    client_secret_ciphertext varchar(500),
    redirect_uri varchar(500) NOT NULL,
    scopes varchar(500) NOT NULL DEFAULT 'openid profile email',
    enabled boolean NOT NULL DEFAULT true,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_identity_provider_tenant_key UNIQUE (tenant_id, provider_key),
    CONSTRAINT uq_identity_provider_tenant_id UNIQUE (tenant_id, id)
);
CREATE INDEX ix_identity_provider_enabled ON identity_provider(tenant_id, enabled);

CREATE TABLE external_storage_binding (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL UNIQUE REFERENCES tenant(id) ON DELETE CASCADE,
    provider_type varchar(32) NOT NULL CHECK (provider_type IN ('LOCAL','S3_COMPATIBLE','GOOGLE_DRIVE','GOOGLE_SHARED_DRIVE')),
    object_prefix varchar(200) NOT NULL DEFAULT '',
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_external_storage_binding_tenant_id UNIQUE (tenant_id, id)
);

CREATE TABLE external_notification_provider (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    provider_key varchar(64) NOT NULL,
    provider_type varchar(24) NOT NULL CHECK (provider_type IN ('HTTP_JSON','SLACK_WEBHOOK','TEAMS_WEBHOOK')),
    endpoint varchar(1000) NOT NULL,
    credential_ciphertext varchar(500),
    enabled boolean NOT NULL DEFAULT true,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_external_notification_tenant_key UNIQUE (tenant_id, provider_key),
    CONSTRAINT uq_external_notification_tenant_id UNIQUE (tenant_id, id)
);
CREATE INDEX ix_external_notification_enabled ON external_notification_provider(tenant_id, enabled);

-- Upgrade existing built-in roles without changing custom role assignments.
INSERT INTO role_permission(role_id, permission_key)
SELECT rd.id, p.permission_key
FROM role_definition rd
CROSS JOIN (VALUES
 ('API_VIEW'),('API_MANAGE'),('WEBHOOKS_VIEW'),('WEBHOOKS_MANAGE'),('FEDERATION_VIEW'),('FEDERATION_MANAGE'),
 ('INTEROPERABILITY_VIEW'),('INTEROPERABILITY_MANAGE'),('EXTERNAL_STORAGE_MANAGE'),('EXTERNAL_NOTIFICATIONS_MANAGE')
) p(permission_key)
WHERE rd.system_key IN ('ORGANIZATION_OWNER','ORGANIZATION_ADMINISTRATOR')
ON CONFLICT DO NOTHING;
INSERT INTO role_permission(role_id, permission_key)
SELECT rd.id, p.permission_key FROM role_definition rd CROSS JOIN (VALUES
 ('API_VIEW'),('WEBHOOKS_VIEW'),('FEDERATION_VIEW'),('INTEROPERABILITY_VIEW')
) p(permission_key) WHERE rd.system_key IN ('SUPPORT_OPERATOR','AUDITOR') ON CONFLICT DO NOTHING;
INSERT INTO role_permission(role_id, permission_key)
SELECT rd.id, p.permission_key FROM role_definition rd CROSS JOIN (VALUES
 ('API_VIEW'),('INTEROPERABILITY_VIEW'),('INTEROPERABILITY_MANAGE'),('FEDERATION_VIEW')
) p(permission_key) WHERE rd.system_key='ACADEMIC_ADMINISTRATOR' ON CONFLICT DO NOTHING;
