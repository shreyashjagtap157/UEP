CREATE TABLE storage_object (
 id uuid PRIMARY KEY DEFAULT uuidv7(), tenant_id uuid NOT NULL, provider varchar(32) NOT NULL,
 locator varchar(2048) NOT NULL, object_key varchar(1024) NOT NULL, content_type varchar(255) NOT NULL,
 size_bytes bigint NOT NULL CHECK(size_bytes >= 0), sha256 varchar(64) NOT NULL,
 status varchar(32) NOT NULL, scan_status varchar(32) NOT NULL, created_at timestamptz NOT NULL, deleted_at timestamptz,
 UNIQUE(tenant_id,id), UNIQUE(tenant_id,provider,locator)
);
CREATE INDEX idx_storage_object_tenant_created ON storage_object(tenant_id,created_at DESC);
CREATE TABLE upload_session (
 id uuid PRIMARY KEY DEFAULT uuidv7(), tenant_id uuid NOT NULL, created_by varchar(255) NOT NULL,
 file_name varchar(512) NOT NULL, content_type varchar(255) NOT NULL, expected_bytes bigint NOT NULL CHECK(expected_bytes >= 0),
 received_bytes bigint NOT NULL DEFAULT 0 CHECK(received_bytes >= 0), expected_sha256 varchar(64),
 provider varchar(32) NOT NULL, status varchar(32) NOT NULL, staging_path varchar(2048) NOT NULL,
 created_at timestamptz NOT NULL, expires_at timestamptz NOT NULL, completed_at timestamptz,
 UNIQUE(tenant_id,id)
);
CREATE INDEX idx_upload_session_expiry ON upload_session(status,expires_at);
CREATE TABLE learning_resource (
 id uuid PRIMARY KEY DEFAULT uuidv7(), tenant_id uuid NOT NULL, stable_key uuid NOT NULL,
 title varchar(300) NOT NULL, description varchar(4000), resource_kind varchar(32) NOT NULL,
 course_id uuid, module_id uuid, class_session_id uuid, language varchar(35),
 visibility varchar(32) NOT NULL, download_policy varchar(32) NOT NULL,
 release_at timestamptz, expires_at timestamptz, current_version integer NOT NULL CHECK(current_version > 0),
 created_by varchar(255) NOT NULL, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL, row_version bigint NOT NULL DEFAULT 0,
 UNIQUE(tenant_id,id), UNIQUE(tenant_id,stable_key)
);
CREATE INDEX idx_learning_resource_tenant_release ON learning_resource(tenant_id,release_at,id);
CREATE TABLE learning_resource_version (
 id uuid PRIMARY KEY DEFAULT uuidv7(), tenant_id uuid NOT NULL, resource_id uuid NOT NULL, version_number integer NOT NULL CHECK(version_number > 0),
 storage_object_id uuid, textual_content text, change_note varchar(1000), created_by varchar(255) NOT NULL, created_at timestamptz NOT NULL,
 UNIQUE(tenant_id,id), UNIQUE(tenant_id,resource_id,version_number),
 FOREIGN KEY(tenant_id,resource_id) REFERENCES learning_resource(tenant_id,id),
 FOREIGN KEY(tenant_id,storage_object_id) REFERENCES storage_object(tenant_id,id),
 CHECK ((storage_object_id IS NOT NULL) <> (textual_content IS NOT NULL))
);
CREATE TABLE content_retention_policy (
 tenant_id uuid PRIMARY KEY, default_retention_days integer CHECK(default_retention_days IS NULL OR default_retention_days > 0),
 deleted_object_grace_days integer NOT NULL DEFAULT 30 CHECK(deleted_object_grace_days >= 0),
 retain_versions boolean NOT NULL DEFAULT true, updated_at timestamptz NOT NULL, row_version bigint NOT NULL DEFAULT 0
);
