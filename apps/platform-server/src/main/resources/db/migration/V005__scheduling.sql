CREATE TABLE schedule_series (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    kind varchar(32) NOT NULL CHECK (kind IN ('CLASS', 'EXAM', 'ASSIGNMENT', 'MEETING', 'EVENT', 'HOLIDAY', 'APPOINTMENT')),
    title varchar(240) NOT NULL,
    description varchar(4000),
    timezone varchar(80) NOT NULL,
    delivery_mode varchar(32) NOT NULL CHECK (delivery_mode IN ('OFFLINE', 'ONLINE', 'HYBRID', 'NOT_APPLICABLE')),
    branch_id uuid,
    batch_id uuid,
    course_id uuid,
    subject_id uuid,
    module_id uuid,
    primary_teacher_membership_id uuid,
    room_code varchar(120),
    start_local timestamp NOT NULL,
    duration_minutes integer NOT NULL CHECK (duration_minutes > 0 AND duration_minutes <= 10080),
    recurrence_frequency varchar(24) NOT NULL CHECK (recurrence_frequency IN ('NONE', 'DAILY', 'WEEKLY', 'MONTHLY')),
    recurrence_interval integer NOT NULL DEFAULT 1 CHECK (recurrence_interval BETWEEN 1 AND 365),
    recurrence_days varchar(32),
    recurrence_day_of_month smallint CHECK (recurrence_day_of_month BETWEEN 1 AND 31),
    recurrence_until_local timestamp,
    recurrence_count integer CHECK (recurrence_count IS NULL OR recurrence_count BETWEEN 1 AND 1000),
    status varchar(24) NOT NULL CHECK (status IN ('ACTIVE', 'CANCELLED')),
    created_by_subject varchar(160) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_schedule_series_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_schedule_series_branch FOREIGN KEY (tenant_id, branch_id)
        REFERENCES branch(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_schedule_series_batch FOREIGN KEY (tenant_id, batch_id)
        REFERENCES batch(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_schedule_series_course FOREIGN KEY (tenant_id, course_id)
        REFERENCES course(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_schedule_series_subject FOREIGN KEY (tenant_id, subject_id)
        REFERENCES subject(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_schedule_series_module FOREIGN KEY (tenant_id, module_id)
        REFERENCES curriculum_module(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_schedule_series_teacher FOREIGN KEY (tenant_id, primary_teacher_membership_id)
        REFERENCES tenant_membership(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_schedule_recurrence_shape CHECK (
        (recurrence_frequency = 'NONE' AND recurrence_days IS NULL AND recurrence_day_of_month IS NULL AND recurrence_count IS NULL AND recurrence_until_local IS NULL)
        OR
        (recurrence_frequency = 'DAILY' AND recurrence_days IS NULL AND recurrence_day_of_month IS NULL)
        OR
        (recurrence_frequency = 'WEEKLY' AND recurrence_days IS NOT NULL AND recurrence_day_of_month IS NULL)
        OR
        (recurrence_frequency = 'MONTHLY' AND recurrence_days IS NULL AND recurrence_day_of_month IS NOT NULL)
    ),
    CONSTRAINT ck_schedule_recurrence_end CHECK (recurrence_count IS NULL OR recurrence_until_local IS NULL),
    CONSTRAINT ck_schedule_delivery CHECK (
        (kind = 'HOLIDAY' AND delivery_mode = 'NOT_APPLICABLE') OR kind <> 'HOLIDAY'
    )
);

CREATE TABLE schedule_occurrence (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    series_id uuid NOT NULL,
    original_starts_at timestamptz NOT NULL,
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    status varchar(24) NOT NULL CHECK (status IN ('SCHEDULED', 'CANCELLED', 'COMPLETED')),
    substitute_teacher_membership_id uuid,
    room_code_override varchar(120),
    exception_reason varchar(500),
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_schedule_occurrence_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_schedule_occurrence_series_original UNIQUE (series_id, original_starts_at),
    CONSTRAINT fk_schedule_occurrence_series FOREIGN KEY (tenant_id, series_id)
        REFERENCES schedule_series(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_schedule_occurrence_substitute FOREIGN KEY (tenant_id, substitute_teacher_membership_id)
        REFERENCES tenant_membership(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_schedule_occurrence_times CHECK (ends_at > starts_at)
);

CREATE TABLE class_session (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    schedule_occurrence_id uuid NOT NULL,
    batch_id uuid,
    course_id uuid,
    subject_id uuid,
    module_id uuid,
    status varchar(24) NOT NULL CHECK (status IN ('SCHEDULED', 'CANCELLED', 'COMPLETED')),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_class_session_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_class_session_occurrence UNIQUE (schedule_occurrence_id),
    CONSTRAINT fk_class_session_occurrence FOREIGN KEY (tenant_id, schedule_occurrence_id)
        REFERENCES schedule_occurrence(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_class_session_batch FOREIGN KEY (tenant_id, batch_id)
        REFERENCES batch(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_class_session_course FOREIGN KEY (tenant_id, course_id)
        REFERENCES course(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_class_session_subject FOREIGN KEY (tenant_id, subject_id)
        REFERENCES subject(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_class_session_module FOREIGN KEY (tenant_id, module_id)
        REFERENCES curriculum_module(tenant_id, id) ON DELETE RESTRICT
);

CREATE TABLE schedule_conflict_override (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    series_id uuid,
    occurrence_id uuid,
    reason varchar(500) NOT NULL,
    conflict_summary text NOT NULL,
    actor_subject varchar(160) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_schedule_conflict_override_series FOREIGN KEY (tenant_id, series_id)
        REFERENCES schedule_series(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_schedule_conflict_override_occurrence FOREIGN KEY (tenant_id, occurrence_id)
        REFERENCES schedule_occurrence(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_schedule_conflict_override_target CHECK (num_nonnulls(series_id, occurrence_id) = 1)
);

CREATE INDEX ix_schedule_occurrence_range ON schedule_occurrence (tenant_id, starts_at, ends_at) WHERE status = 'SCHEDULED';
CREATE INDEX ix_schedule_series_teacher ON schedule_series (tenant_id, primary_teacher_membership_id) WHERE primary_teacher_membership_id IS NOT NULL;
CREATE INDEX ix_schedule_series_batch ON schedule_series (tenant_id, batch_id) WHERE batch_id IS NOT NULL;
CREATE INDEX ix_schedule_series_branch_room ON schedule_series (tenant_id, branch_id, room_code) WHERE room_code IS NOT NULL;
CREATE INDEX ix_class_session_occurrence ON class_session (tenant_id, schedule_occurrence_id);
CREATE INDEX ix_schedule_conflict_override_series ON schedule_conflict_override (tenant_id, series_id, created_at DESC);
CREATE INDEX ix_schedule_conflict_override_occurrence ON schedule_conflict_override (tenant_id, occurrence_id, created_at DESC);
