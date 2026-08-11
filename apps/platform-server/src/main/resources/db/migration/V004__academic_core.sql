CREATE TABLE academic_period (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    code varchar(64) NOT NULL,
    display_name varchar(200) NOT NULL,
    starts_on date NOT NULL,
    ends_on date NOT NULL,
    status varchar(32) NOT NULL CHECK (status IN ('PLANNED', 'ACTIVE', 'CLOSED', 'ARCHIVED')),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_academic_period_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_academic_period_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_academic_period_dates CHECK (ends_on >= starts_on)
);

CREATE TABLE academic_program (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    academic_period_id uuid,
    code varchar(64) NOT NULL,
    display_name varchar(200) NOT NULL,
    description varchar(2000),
    status varchar(32) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_academic_program_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_academic_program_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT fk_program_period FOREIGN KEY (tenant_id, academic_period_id)
        REFERENCES academic_period(tenant_id, id) ON DELETE RESTRICT
);

CREATE TABLE course (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    program_id uuid,
    code varchar(64) NOT NULL,
    display_name varchar(200) NOT NULL,
    description varchar(4000),
    status varchar(32) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_course_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_course_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT fk_course_program FOREIGN KEY (tenant_id, program_id)
        REFERENCES academic_program(tenant_id, id) ON DELETE RESTRICT
);

CREATE TABLE subject (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    course_id uuid,
    code varchar(64) NOT NULL,
    display_name varchar(200) NOT NULL,
    description varchar(4000),
    status varchar(32) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_subject_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_subject_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT fk_subject_course FOREIGN KEY (tenant_id, course_id)
        REFERENCES course(tenant_id, id) ON DELETE RESTRICT
);

CREATE TABLE curriculum_module (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    program_id uuid,
    course_id uuid,
    subject_id uuid,
    code varchar(64) NOT NULL,
    display_name varchar(200) NOT NULL,
    description varchar(4000),
    sequence_number integer,
    status varchar(32) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_curriculum_module_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_curriculum_module_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT fk_module_program FOREIGN KEY (tenant_id, program_id)
        REFERENCES academic_program(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_module_course FOREIGN KEY (tenant_id, course_id)
        REFERENCES course(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_module_subject FOREIGN KEY (tenant_id, subject_id)
        REFERENCES subject(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_module_single_parent CHECK (num_nonnulls(program_id, course_id, subject_id) <= 1),
    CONSTRAINT ck_module_sequence CHECK (sequence_number IS NULL OR sequence_number > 0)
);

CREATE TABLE batch (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    academic_period_id uuid,
    program_id uuid,
    course_id uuid,
    branch_id uuid,
    code varchar(64) NOT NULL,
    display_name varchar(200) NOT NULL,
    section_code varchar(64),
    starts_on date,
    ends_on date,
    capacity integer,
    status varchar(32) NOT NULL CHECK (status IN ('PLANNED', 'ACTIVE', 'CLOSED', 'ARCHIVED')),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_batch_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_batch_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT fk_batch_period FOREIGN KEY (tenant_id, academic_period_id)
        REFERENCES academic_period(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_batch_program FOREIGN KEY (tenant_id, program_id)
        REFERENCES academic_program(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_batch_course FOREIGN KEY (tenant_id, course_id)
        REFERENCES course(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_batch_branch FOREIGN KEY (tenant_id, branch_id)
        REFERENCES branch(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_batch_dates CHECK (ends_on IS NULL OR starts_on IS NULL OR ends_on >= starts_on),
    CONSTRAINT ck_batch_capacity CHECK (capacity IS NULL OR capacity > 0)
);

CREATE TABLE enrollment (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    membership_id uuid NOT NULL,
    program_id uuid,
    course_id uuid,
    batch_id uuid,
    status varchar(32) NOT NULL CHECK (status IN ('ENROLLED', 'COMPLETED', 'WITHDRAWN', 'CANCELLED')),
    enrolled_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at timestamptz,
    external_reference varchar(160),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_enrollment_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_enrollment_membership FOREIGN KEY (tenant_id, membership_id)
        REFERENCES tenant_membership(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_enrollment_program FOREIGN KEY (tenant_id, program_id)
        REFERENCES academic_program(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_enrollment_course FOREIGN KEY (tenant_id, course_id)
        REFERENCES course(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_enrollment_batch FOREIGN KEY (tenant_id, batch_id)
        REFERENCES batch(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_enrollment_single_target CHECK (num_nonnulls(program_id, course_id, batch_id) = 1),
    CONSTRAINT ck_enrollment_ended CHECK (
        (status IN ('COMPLETED', 'WITHDRAWN', 'CANCELLED') AND ended_at IS NOT NULL)
        OR (status = 'ENROLLED' AND ended_at IS NULL)
    )
);

CREATE UNIQUE INDEX uq_enrollment_active_program
    ON enrollment (tenant_id, membership_id, program_id)
    WHERE program_id IS NOT NULL AND status = 'ENROLLED';
CREATE UNIQUE INDEX uq_enrollment_active_course
    ON enrollment (tenant_id, membership_id, course_id)
    WHERE course_id IS NOT NULL AND status = 'ENROLLED';
CREATE UNIQUE INDEX uq_enrollment_active_batch
    ON enrollment (tenant_id, membership_id, batch_id)
    WHERE batch_id IS NOT NULL AND status = 'ENROLLED';

CREATE TABLE teacher_assignment (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    membership_id uuid NOT NULL,
    program_id uuid,
    course_id uuid,
    subject_id uuid,
    module_id uuid,
    batch_id uuid,
    assignment_role varchar(40) NOT NULL CHECK (assignment_role IN ('LEAD_TEACHER', 'TEACHER', 'TEACHING_ASSISTANT', 'MENTOR', 'EVALUATOR')),
    starts_on date,
    ends_on date,
    status varchar(32) NOT NULL CHECK (status IN ('ACTIVE', 'ENDED')),
    assigned_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by_subject varchar(160) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_teacher_assignment_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_teacher_assignment_membership FOREIGN KEY (tenant_id, membership_id)
        REFERENCES tenant_membership(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_teacher_assignment_program FOREIGN KEY (tenant_id, program_id)
        REFERENCES academic_program(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_teacher_assignment_course FOREIGN KEY (tenant_id, course_id)
        REFERENCES course(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_teacher_assignment_subject FOREIGN KEY (tenant_id, subject_id)
        REFERENCES subject(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_teacher_assignment_module FOREIGN KEY (tenant_id, module_id)
        REFERENCES curriculum_module(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_teacher_assignment_batch FOREIGN KEY (tenant_id, batch_id)
        REFERENCES batch(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_teacher_assignment_single_scope CHECK (num_nonnulls(program_id, course_id, subject_id, module_id, batch_id) = 1),
    CONSTRAINT ck_teacher_assignment_dates CHECK (ends_on IS NULL OR starts_on IS NULL OR ends_on >= starts_on),
    CONSTRAINT ck_teacher_assignment_status CHECK ((status = 'ENDED' AND ends_on IS NOT NULL) OR status = 'ACTIVE')
);

CREATE UNIQUE INDEX uq_teacher_assignment_active_program
    ON teacher_assignment (tenant_id, membership_id, program_id, assignment_role)
    WHERE program_id IS NOT NULL AND status = 'ACTIVE';
CREATE UNIQUE INDEX uq_teacher_assignment_active_course
    ON teacher_assignment (tenant_id, membership_id, course_id, assignment_role)
    WHERE course_id IS NOT NULL AND status = 'ACTIVE';
CREATE UNIQUE INDEX uq_teacher_assignment_active_subject
    ON teacher_assignment (tenant_id, membership_id, subject_id, assignment_role)
    WHERE subject_id IS NOT NULL AND status = 'ACTIVE';
CREATE UNIQUE INDEX uq_teacher_assignment_active_module
    ON teacher_assignment (tenant_id, membership_id, module_id, assignment_role)
    WHERE module_id IS NOT NULL AND status = 'ACTIVE';
CREATE UNIQUE INDEX uq_teacher_assignment_active_batch
    ON teacher_assignment (tenant_id, membership_id, batch_id, assignment_role)
    WHERE batch_id IS NOT NULL AND status = 'ACTIVE';

CREATE INDEX ix_academic_period_tenant_status ON academic_period (tenant_id, status, starts_on DESC);
CREATE INDEX ix_academic_program_period ON academic_program (tenant_id, academic_period_id);
CREATE INDEX ix_course_program ON course (tenant_id, program_id);
CREATE INDEX ix_subject_course ON subject (tenant_id, course_id);
CREATE INDEX ix_module_program ON curriculum_module (tenant_id, program_id) WHERE program_id IS NOT NULL;
CREATE INDEX ix_module_course ON curriculum_module (tenant_id, course_id) WHERE course_id IS NOT NULL;
CREATE INDEX ix_module_subject ON curriculum_module (tenant_id, subject_id) WHERE subject_id IS NOT NULL;
CREATE INDEX ix_batch_period ON batch (tenant_id, academic_period_id);
CREATE INDEX ix_batch_program ON batch (tenant_id, program_id);
CREATE INDEX ix_batch_course ON batch (tenant_id, course_id);
CREATE INDEX ix_batch_branch ON batch (tenant_id, branch_id);
CREATE INDEX ix_enrollment_membership ON enrollment (tenant_id, membership_id, status);
CREATE INDEX ix_enrollment_batch ON enrollment (tenant_id, batch_id, status) WHERE batch_id IS NOT NULL;
CREATE INDEX ix_teacher_assignment_membership ON teacher_assignment (tenant_id, membership_id, status);
CREATE INDEX ix_teacher_assignment_batch ON teacher_assignment (tenant_id, batch_id, status) WHERE batch_id IS NOT NULL;

-- Extend persisted system-role grants for the academic-core milestone. Runtime seeding reconciles the same blueprints.
INSERT INTO role_permission (role_id, permission_key)
SELECT rd.id, grant_map.permission_key
FROM role_definition rd
JOIN (VALUES
    ('ORGANIZATION_OWNER', 'ACADEMICS_VIEW'), ('ORGANIZATION_OWNER', 'ACADEMICS_MANAGE'),
    ('ORGANIZATION_OWNER', 'CURRICULUM_VIEW'), ('ORGANIZATION_OWNER', 'CURRICULUM_MANAGE'),
    ('ORGANIZATION_OWNER', 'ENROLLMENTS_VIEW'), ('ORGANIZATION_OWNER', 'ENROLLMENTS_MANAGE'),
    ('ORGANIZATION_OWNER', 'TEACHING_ASSIGNMENTS_VIEW'), ('ORGANIZATION_OWNER', 'TEACHING_ASSIGNMENTS_MANAGE'),
    ('ORGANIZATION_ADMINISTRATOR', 'ACADEMICS_VIEW'), ('ORGANIZATION_ADMINISTRATOR', 'ACADEMICS_MANAGE'),
    ('ORGANIZATION_ADMINISTRATOR', 'CURRICULUM_VIEW'), ('ORGANIZATION_ADMINISTRATOR', 'CURRICULUM_MANAGE'),
    ('ORGANIZATION_ADMINISTRATOR', 'ENROLLMENTS_VIEW'), ('ORGANIZATION_ADMINISTRATOR', 'ENROLLMENTS_MANAGE'),
    ('ORGANIZATION_ADMINISTRATOR', 'TEACHING_ASSIGNMENTS_VIEW'), ('ORGANIZATION_ADMINISTRATOR', 'TEACHING_ASSIGNMENTS_MANAGE'),
    ('BRANCH_ADMINISTRATOR', 'ACADEMICS_VIEW'), ('BRANCH_ADMINISTRATOR', 'CURRICULUM_VIEW'),
    ('ACADEMIC_ADMINISTRATOR', 'ACADEMICS_VIEW'), ('ACADEMIC_ADMINISTRATOR', 'ACADEMICS_MANAGE'),
    ('ACADEMIC_ADMINISTRATOR', 'CURRICULUM_VIEW'), ('ACADEMIC_ADMINISTRATOR', 'CURRICULUM_MANAGE'),
    ('ACADEMIC_ADMINISTRATOR', 'ENROLLMENTS_VIEW'), ('ACADEMIC_ADMINISTRATOR', 'ENROLLMENTS_MANAGE'),
    ('ACADEMIC_ADMINISTRATOR', 'TEACHING_ASSIGNMENTS_VIEW'), ('ACADEMIC_ADMINISTRATOR', 'TEACHING_ASSIGNMENTS_MANAGE'),
    ('EXAM_CONTROLLER', 'ACADEMICS_VIEW'), ('EXAM_CONTROLLER', 'CURRICULUM_VIEW'),
    ('FINANCE_ADMINISTRATOR', 'ACADEMICS_VIEW'), ('FINANCE_ADMINISTRATOR', 'CURRICULUM_VIEW'),
    ('TEACHER', 'ACADEMICS_VIEW'), ('TEACHER', 'CURRICULUM_VIEW'),
    ('EVALUATOR', 'ACADEMICS_VIEW'), ('EVALUATOR', 'CURRICULUM_VIEW'),
    ('TEACHING_ASSISTANT', 'ACADEMICS_VIEW'), ('TEACHING_ASSISTANT', 'CURRICULUM_VIEW'),
    ('MENTOR', 'ACADEMICS_VIEW'), ('MENTOR', 'CURRICULUM_VIEW'),
    ('STUDENT', 'ACADEMICS_VIEW'), ('STUDENT', 'CURRICULUM_VIEW'),
    ('GUARDIAN', 'ACADEMICS_VIEW'), ('GUARDIAN', 'CURRICULUM_VIEW'),
    ('SUPPORT_OPERATOR', 'ACADEMICS_VIEW'), ('SUPPORT_OPERATOR', 'CURRICULUM_VIEW'),
    ('AUDITOR', 'ACADEMICS_VIEW'), ('AUDITOR', 'CURRICULUM_VIEW'),
    ('AUDITOR', 'ENROLLMENTS_VIEW'), ('AUDITOR', 'TEACHING_ASSIGNMENTS_VIEW')
) AS grant_map(system_key, permission_key)
ON rd.system_key = grant_map.system_key
ON CONFLICT (role_id, permission_key) DO NOTHING;
