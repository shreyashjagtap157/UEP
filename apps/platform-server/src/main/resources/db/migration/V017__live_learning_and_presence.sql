CREATE TABLE live_class (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    class_session_id uuid NOT NULL,
    room_name varchar(160) NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('SCHEDULED','LIVE','ENDED','CANCELLED')),
    attendance_policy varchar(24) NOT NULL CHECK (attendance_policy IN ('MANUAL','JOIN_TIME','MINIMUM_DURATION','PERCENTAGE')),
    minimum_attendance_seconds integer NOT NULL DEFAULT 0 CHECK (minimum_attendance_seconds BETWEEN 0 AND 86400),
    attendance_threshold_basis_points integer NOT NULL DEFAULT 0 CHECK (attendance_threshold_basis_points BETWEEN 0 AND 10000),
    low_bandwidth boolean NOT NULL DEFAULT false,
    chat_enabled boolean NOT NULL DEFAULT true,
    started_at timestamptz,
    ended_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_live_class_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_live_class_session UNIQUE (tenant_id, class_session_id),
    CONSTRAINT uq_live_class_room UNIQUE (tenant_id, room_name),
    CONSTRAINT fk_live_class_session FOREIGN KEY (tenant_id, class_session_id)
        REFERENCES class_session(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_live_class_dates CHECK (ended_at IS NULL OR started_at IS NOT NULL AND ended_at >= started_at),
    CONSTRAINT ck_live_class_threshold CHECK (
        (attendance_policy = 'PERCENTAGE' AND attendance_threshold_basis_points BETWEEN 1 AND 10000)
        OR attendance_policy <> 'PERCENTAGE'
    )
);

CREATE TABLE live_class_participant (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    live_class_id uuid NOT NULL,
    membership_id uuid NOT NULL,
    role varchar(20) NOT NULL CHECK (role IN ('HOST','MODERATOR','PRESENTER','PARTICIPANT','OBSERVER')),
    status varchar(20) NOT NULL CHECK (status IN ('INVITED','JOINED','LEFT','MUTED','KICKED','BLOCKED')),
    invited_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    joined_at timestamptz,
    left_at timestamptz,
    total_present_seconds bigint NOT NULL DEFAULT 0 CHECK (total_present_seconds >= 0),
    last_client_profile varchar(40),
    moderation_reason varchar(500),
    last_heartbeat_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_live_class_participant UNIQUE (tenant_id, live_class_id, membership_id),
    CONSTRAINT fk_live_class_participant_class FOREIGN KEY (tenant_id, live_class_id)
        REFERENCES live_class(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_live_class_participant_membership FOREIGN KEY (tenant_id, membership_id)
        REFERENCES tenant_membership(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_live_class_participant_dates CHECK (left_at IS NULL OR joined_at IS NOT NULL AND left_at >= joined_at)
);

CREATE TABLE attendance_record (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    live_class_id uuid NOT NULL,
    membership_id uuid NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('PRESENT','PARTIAL','ABSENT','EXCUSED')),
    present_seconds bigint NOT NULL CHECK (present_seconds >= 0),
    finalized_at timestamptz NOT NULL,
    source varchar(16) NOT NULL CHECK (source IN ('AUTO','MANUAL')),
    notes varchar(500),
    CONSTRAINT uq_attendance_record_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_attendance_record UNIQUE (tenant_id, live_class_id, membership_id),
    CONSTRAINT fk_attendance_live_class FOREIGN KEY (tenant_id, live_class_id)
        REFERENCES live_class(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_attendance_membership FOREIGN KEY (tenant_id, membership_id)
        REFERENCES tenant_membership(tenant_id, id) ON DELETE CASCADE
);

CREATE INDEX ix_live_class_status ON live_class (tenant_id, status, created_at);
CREATE INDEX ix_live_class_participant_heartbeat ON live_class_participant (tenant_id, live_class_id, last_heartbeat_at);
CREATE INDEX ix_live_class_participant_membership ON live_class_participant (tenant_id, membership_id, status);
CREATE INDEX ix_attendance_live_class ON attendance_record (tenant_id, live_class_id, status);

INSERT INTO role_permission (role_id, permission_key)
SELECT rd.id, rp.permission_key
FROM role_definition rd
JOIN (VALUES
 ('ORGANIZATION_OWNER','LIVE_CLASS_VIEW'),('ORGANIZATION_OWNER','LIVE_CLASS_MANAGE'),('ORGANIZATION_OWNER','LIVE_CLASS_MODERATE'),('ORGANIZATION_OWNER','LIVE_CLASS_CHAT'),('ORGANIZATION_OWNER','PRESENCE_VIEW'),('ORGANIZATION_OWNER','PRESENCE_MANAGE'),('ORGANIZATION_OWNER','ATTENDANCE_VIEW'),('ORGANIZATION_OWNER','ATTENDANCE_MANAGE'),
 ('ORGANIZATION_ADMINISTRATOR','LIVE_CLASS_VIEW'),('ORGANIZATION_ADMINISTRATOR','LIVE_CLASS_MANAGE'),('ORGANIZATION_ADMINISTRATOR','LIVE_CLASS_MODERATE'),('ORGANIZATION_ADMINISTRATOR','LIVE_CLASS_CHAT'),('ORGANIZATION_ADMINISTRATOR','PRESENCE_VIEW'),('ORGANIZATION_ADMINISTRATOR','PRESENCE_MANAGE'),('ORGANIZATION_ADMINISTRATOR','ATTENDANCE_VIEW'),('ORGANIZATION_ADMINISTRATOR','ATTENDANCE_MANAGE'),
 ('ACADEMIC_ADMINISTRATOR','LIVE_CLASS_VIEW'),('ACADEMIC_ADMINISTRATOR','LIVE_CLASS_MANAGE'),('ACADEMIC_ADMINISTRATOR','LIVE_CLASS_MODERATE'),('ACADEMIC_ADMINISTRATOR','LIVE_CLASS_CHAT'),('ACADEMIC_ADMINISTRATOR','PRESENCE_VIEW'),('ACADEMIC_ADMINISTRATOR','PRESENCE_MANAGE'),('ACADEMIC_ADMINISTRATOR','ATTENDANCE_VIEW'),('ACADEMIC_ADMINISTRATOR','ATTENDANCE_MANAGE'),
 ('EXAM_CONTROLLER','LIVE_CLASS_VIEW'),('EXAM_CONTROLLER','LIVE_CLASS_MANAGE'),('EXAM_CONTROLLER','LIVE_CLASS_MODERATE'),('EXAM_CONTROLLER','LIVE_CLASS_CHAT'),('EXAM_CONTROLLER','PRESENCE_VIEW'),('EXAM_CONTROLLER','ATTENDANCE_VIEW'),
 ('TEACHER','LIVE_CLASS_VIEW'),('TEACHER','LIVE_CLASS_MANAGE'),('TEACHER','LIVE_CLASS_MODERATE'),('TEACHER','LIVE_CLASS_CHAT'),('TEACHER','PRESENCE_VIEW'),('TEACHER','PRESENCE_MANAGE'),('TEACHER','ATTENDANCE_VIEW'),('TEACHER','ATTENDANCE_MANAGE'),
 ('TEACHING_ASSISTANT','LIVE_CLASS_VIEW'),('TEACHING_ASSISTANT','LIVE_CLASS_CHAT'),('TEACHING_ASSISTANT','PRESENCE_VIEW'),('TEACHING_ASSISTANT','ATTENDANCE_VIEW'),
 ('MENTOR','LIVE_CLASS_VIEW'),('MENTOR','LIVE_CLASS_CHAT'),('MENTOR','PRESENCE_VIEW'),('MENTOR','ATTENDANCE_VIEW'),
 ('EVALUATOR','LIVE_CLASS_VIEW'),('EVALUATOR','LIVE_CLASS_CHAT'),('EVALUATOR','PRESENCE_VIEW'),('EVALUATOR','ATTENDANCE_VIEW'),
 ('STUDENT','LIVE_CLASS_VIEW'),('STUDENT','LIVE_CLASS_CHAT'),('STUDENT','PRESENCE_VIEW'),
 ('GUARDIAN','LIVE_CLASS_VIEW'),('GUARDIAN','LIVE_CLASS_CHAT'),('GUARDIAN','PRESENCE_VIEW')
) AS rp(system_key,permission_key) ON rd.system_key=rp.system_key
ON CONFLICT (role_id,permission_key) DO NOTHING;
