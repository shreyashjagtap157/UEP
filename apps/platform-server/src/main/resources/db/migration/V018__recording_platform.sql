CREATE TABLE recording_storage_policy (
    tenant_id uuid PRIMARY KEY,
    hot_provider varchar(32) NOT NULL,
    cache_provider varchar(32) NOT NULL,
    archive_provider varchar(32) NOT NULL,
    hot_cache_days integer NOT NULL DEFAULT 14,
    cache_days integer NOT NULL DEFAULT 30,
    retention_days integer,
    deleted_object_grace_days integer NOT NULL DEFAULT 30,
    updated_at timestamptz NOT NULL,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_recording_policy_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id),
    CONSTRAINT ck_recording_policy_hot CHECK(hot_cache_days >= 0),
    CONSTRAINT ck_recording_policy_cache CHECK(cache_days >= 0),
    CONSTRAINT ck_recording_policy_retention CHECK(retention_days IS NULL OR retention_days > 0),
    CONSTRAINT ck_recording_policy_grace CHECK(deleted_object_grace_days >= 0)
);

CREATE TABLE recording (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    live_class_id uuid NOT NULL,
    class_session_id uuid NOT NULL,
    room_name varchar(160) NOT NULL,
    quality_preset varchar(24) NOT NULL,
    status varchar(24) NOT NULL,
    storage_tier varchar(16) NOT NULL,
    storage_provider varchar(32) NOT NULL,
    storage_locator varchar(2048),
    object_key varchar(1024),
    sha256 varchar(64),
    size_bytes bigint,
    duration_seconds bigint,
    egress_id varchar(160),
    requested_at timestamptz NOT NULL,
    started_at timestamptz,
    ended_at timestamptz,
    ready_at timestamptz,
    archived_at timestamptz,
    deleted_at timestamptz,
    failure_reason varchar(1000),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_recording_tenant_id UNIQUE(tenant_id,id),
    CONSTRAINT fk_recording_live_class FOREIGN KEY(tenant_id,live_class_id) REFERENCES live_class(tenant_id,id),
    CONSTRAINT fk_recording_class_session FOREIGN KEY(tenant_id,class_session_id) REFERENCES class_session(tenant_id,id),
    CONSTRAINT ck_recording_size CHECK(size_bytes IS NULL OR size_bytes >= 0),
    CONSTRAINT ck_recording_duration CHECK(duration_seconds IS NULL OR duration_seconds >= 0),
    CONSTRAINT ck_recording_tier CHECK(storage_tier IN ('HOT','CACHE','ARCHIVE'))
);
CREATE UNIQUE INDEX uq_recording_live_class_active ON recording(tenant_id,live_class_id) WHERE status <> 'DELETED';
CREATE INDEX idx_recording_status_requested ON recording(status,requested_at);
CREATE INDEX idx_recording_ready ON recording(status,ready_at);

CREATE TABLE recording_asset (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    recording_id uuid NOT NULL,
    kind varchar(20) NOT NULL,
    provider varchar(32) NOT NULL,
    locator varchar(2048) NOT NULL,
    object_key varchar(1024) NOT NULL,
    mime_type varchar(160) NOT NULL,
    size_bytes bigint NOT NULL,
    sha256 varchar(64) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_recording_asset_tenant_id UNIQUE(tenant_id,id),
    CONSTRAINT uq_recording_asset_kind UNIQUE(tenant_id,recording_id,kind),
    CONSTRAINT fk_recording_asset_recording FOREIGN KEY(tenant_id,recording_id) REFERENCES recording(tenant_id,id),
    CONSTRAINT ck_recording_asset_size CHECK(size_bytes >= 0)
);
CREATE INDEX idx_recording_asset_recording ON recording_asset(tenant_id,recording_id);

INSERT INTO role_permission (role_id, permission_key)
SELECT rd.id, grant_map.permission_key FROM role_definition rd JOIN (VALUES
 ('ORGANIZATION_OWNER','RECORDINGS_VIEW'),('ORGANIZATION_OWNER','RECORDINGS_MANAGE'),
 ('ORGANIZATION_ADMINISTRATOR','RECORDINGS_VIEW'),('ORGANIZATION_ADMINISTRATOR','RECORDINGS_MANAGE'),
 ('BRANCH_ADMINISTRATOR','RECORDINGS_VIEW'),('BRANCH_ADMINISTRATOR','RECORDINGS_MANAGE'),
 ('ACADEMIC_ADMINISTRATOR','RECORDINGS_VIEW'),('ACADEMIC_ADMINISTRATOR','RECORDINGS_MANAGE'),
 ('TEACHER','RECORDINGS_VIEW'),('TEACHER','RECORDINGS_MANAGE'),
 ('TEACHING_ASSISTANT','RECORDINGS_VIEW'),('TEACHING_ASSISTANT','RECORDINGS_MANAGE'),
 ('MENTOR','RECORDINGS_VIEW'),
 ('STUDENT','RECORDINGS_VIEW'),('GUARDIAN','RECORDINGS_VIEW'),
 ('EXAM_CONTROLLER','RECORDINGS_VIEW'),('EXAM_CONTROLLER','RECORDINGS_MANAGE')
) AS grant_map(system_key,permission_key) ON rd.system_key=grant_map.system_key ON CONFLICT(role_id,permission_key) DO NOTHING;
