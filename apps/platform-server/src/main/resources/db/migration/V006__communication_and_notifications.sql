CREATE TABLE announcement (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    authorization_branch_id uuid,
    title varchar(240) NOT NULL,
    body text NOT NULL,
    priority varchar(24) NOT NULL CHECK (priority IN ('NORMAL', 'IMPORTANT', 'URGENT', 'EMERGENCY')),
    status varchar(24) NOT NULL CHECK (status IN ('DRAFT', 'SCHEDULED', 'PUBLISHED', 'EXPIRED', 'CANCELLED')),
    publish_at timestamptz,
    expires_at timestamptz,
    acknowledgement_required boolean NOT NULL DEFAULT false,
    created_by_subject varchar(160) NOT NULL,
    published_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_announcement_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_announcement_authorization_branch FOREIGN KEY (tenant_id, authorization_branch_id)
        REFERENCES branch(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_announcement_publish_window CHECK (expires_at IS NULL OR publish_at IS NULL OR expires_at > publish_at),
    CONSTRAINT ck_announcement_published CHECK ((status = 'PUBLISHED' AND published_at IS NOT NULL) OR status <> 'PUBLISHED')
);

CREATE TABLE announcement_target (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    announcement_id uuid NOT NULL,
    target_kind varchar(24) NOT NULL CHECK (target_kind IN ('ORGANIZATION', 'BRANCH', 'COURSE', 'BATCH', 'SUBJECT', 'ROLE', 'MEMBERSHIP')),
    branch_id uuid,
    course_id uuid,
    batch_id uuid,
    subject_id uuid,
    role_id uuid,
    membership_id uuid,
    CONSTRAINT fk_announcement_target_announcement FOREIGN KEY (tenant_id, announcement_id)
        REFERENCES announcement(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_announcement_target_branch FOREIGN KEY (tenant_id, branch_id)
        REFERENCES branch(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_announcement_target_course FOREIGN KEY (tenant_id, course_id)
        REFERENCES course(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_announcement_target_batch FOREIGN KEY (tenant_id, batch_id)
        REFERENCES batch(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_announcement_target_subject FOREIGN KEY (tenant_id, subject_id)
        REFERENCES subject(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_announcement_target_role FOREIGN KEY (tenant_id, role_id)
        REFERENCES role_definition(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_announcement_target_membership FOREIGN KEY (tenant_id, membership_id)
        REFERENCES tenant_membership(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_announcement_target_shape CHECK (
        (target_kind = 'ORGANIZATION' AND num_nonnulls(branch_id, course_id, batch_id, subject_id, role_id, membership_id) = 0) OR
        (target_kind = 'BRANCH' AND branch_id IS NOT NULL AND num_nonnulls(course_id, batch_id, subject_id, role_id, membership_id) = 0) OR
        (target_kind = 'COURSE' AND course_id IS NOT NULL AND num_nonnulls(branch_id, batch_id, subject_id, role_id, membership_id) = 0) OR
        (target_kind = 'BATCH' AND batch_id IS NOT NULL AND num_nonnulls(branch_id, course_id, subject_id, role_id, membership_id) = 0) OR
        (target_kind = 'SUBJECT' AND subject_id IS NOT NULL AND num_nonnulls(branch_id, course_id, batch_id, role_id, membership_id) = 0) OR
        (target_kind = 'ROLE' AND role_id IS NOT NULL AND num_nonnulls(branch_id, course_id, batch_id, subject_id, membership_id) = 0) OR
        (target_kind = 'MEMBERSHIP' AND membership_id IS NOT NULL AND num_nonnulls(branch_id, course_id, batch_id, subject_id, role_id) = 0)
    )
);

CREATE TABLE announcement_recipient (
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    announcement_id uuid NOT NULL,
    membership_id uuid NOT NULL,
    resolved_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (announcement_id, membership_id),
    CONSTRAINT fk_announcement_recipient_announcement FOREIGN KEY (tenant_id, announcement_id)
        REFERENCES announcement(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_announcement_recipient_membership FOREIGN KEY (tenant_id, membership_id)
        REFERENCES tenant_membership(tenant_id, id) ON DELETE CASCADE
);

CREATE TABLE announcement_acknowledgement (
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    announcement_id uuid NOT NULL,
    membership_id uuid NOT NULL,
    acknowledged_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (announcement_id, membership_id),
    CONSTRAINT fk_announcement_ack_announcement FOREIGN KEY (tenant_id, announcement_id)
        REFERENCES announcement(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_announcement_ack_membership FOREIGN KEY (tenant_id, membership_id)
        REFERENCES tenant_membership(tenant_id, id) ON DELETE CASCADE
);

CREATE TABLE notification_outbox (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    event_type varchar(64) NOT NULL,
    aggregate_type varchar(64) NOT NULL,
    aggregate_id uuid NOT NULL,
    title varchar(240) NOT NULL,
    body varchar(4000) NOT NULL,
    priority varchar(24) NOT NULL CHECK (priority IN ('NORMAL', 'IMPORTANT', 'URGENT', 'EMERGENCY')),
    resource_type varchar(64),
    resource_id uuid,
    deduplication_key varchar(240) NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    available_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    processed_at timestamptz,
    dead_lettered_at timestamptz,
    last_error varchar(1000),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_notification_outbox_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_notification_outbox_tenant_dedup UNIQUE (tenant_id, deduplication_key)
);

CREATE TABLE notification_preference (
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    membership_id uuid NOT NULL,
    event_type varchar(64) NOT NULL,
    in_app_enabled boolean NOT NULL DEFAULT true,
    email_enabled boolean NOT NULL DEFAULT true,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (membership_id, event_type),
    CONSTRAINT fk_notification_preference_membership FOREIGN KEY (tenant_id, membership_id)
        REFERENCES tenant_membership(tenant_id, id) ON DELETE CASCADE
);

CREATE TABLE notification_outbox_recipient (
    outbox_id uuid NOT NULL,
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    membership_id uuid NOT NULL,
    email varchar(320),
    PRIMARY KEY (outbox_id, membership_id),
    CONSTRAINT fk_notification_outbox_recipient_outbox FOREIGN KEY (tenant_id, outbox_id)
        REFERENCES notification_outbox(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_outbox_recipient_membership FOREIGN KEY (tenant_id, membership_id)
        REFERENCES tenant_membership(tenant_id, id) ON DELETE CASCADE
);

CREATE TABLE notification (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    membership_id uuid NOT NULL,
    source_outbox_id uuid NOT NULL,
    event_type varchar(64) NOT NULL,
    title varchar(240) NOT NULL,
    body varchar(4000) NOT NULL,
    resource_type varchar(64),
    resource_id uuid,
    priority varchar(24) NOT NULL CHECK (priority IN ('NORMAL', 'IMPORTANT', 'URGENT', 'EMERGENCY')),
    visible_in_app boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_notification_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_notification_outbox_member UNIQUE (source_outbox_id, membership_id),
    CONSTRAINT fk_notification_source_outbox FOREIGN KEY (tenant_id, source_outbox_id)
        REFERENCES notification_outbox(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_notification_membership FOREIGN KEY (tenant_id, membership_id)
        REFERENCES tenant_membership(tenant_id, id) ON DELETE CASCADE
);

CREATE TABLE notification_delivery (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    notification_id uuid NOT NULL,
    channel varchar(24) NOT NULL CHECK (channel IN ('EMAIL')),
    destination varchar(320) NOT NULL,
    status varchar(24) NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED')),
    attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at timestamptz,
    last_error varchar(1000),
    idempotency_key varchar(240) NOT NULL UNIQUE,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_notification_delivery_notification FOREIGN KEY (tenant_id, notification_id)
        REFERENCES notification(tenant_id, id) ON DELETE CASCADE
);

CREATE INDEX ix_announcement_publish ON announcement (tenant_id, status, publish_at);
CREATE INDEX ix_announcement_authorization_scope ON announcement (tenant_id, authorization_branch_id, created_at DESC);
CREATE INDEX ix_announcement_target_announcement ON announcement_target (tenant_id, announcement_id);
CREATE INDEX ix_announcement_recipient_member ON announcement_recipient (tenant_id, membership_id, resolved_at DESC);
CREATE INDEX ix_notification_member_time ON notification (tenant_id, membership_id, created_at DESC) WHERE visible_in_app;
CREATE INDEX ix_notification_unread ON notification (tenant_id, membership_id, created_at DESC) WHERE read_at IS NULL;
CREATE INDEX ix_notification_outbox_pending ON notification_outbox (available_at, occurred_at) WHERE processed_at IS NULL AND dead_lettered_at IS NULL;
CREATE INDEX ix_notification_outbox_recipient_member ON notification_outbox_recipient (tenant_id, membership_id);
CREATE INDEX ix_notification_delivery_pending ON notification_delivery (next_attempt_at) WHERE status IN ('PENDING', 'FAILED');
