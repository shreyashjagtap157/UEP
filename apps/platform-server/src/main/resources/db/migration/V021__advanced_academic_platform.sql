CREATE TABLE learning_outcome (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    code varchar(80) NOT NULL,
    name varchar(200) NOT NULL,
    description varchar(2000),
    status varchar(24) NOT NULL CHECK (status IN ('ACTIVE','INACTIVE','ARCHIVED')),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_learning_outcome_tenant_id UNIQUE (tenant_id,id),
    CONSTRAINT uq_learning_outcome_tenant_code UNIQUE (tenant_id,code)
);
CREATE TABLE competency (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    outcome_id uuid NOT NULL,
    code varchar(80) NOT NULL,
    name varchar(200) NOT NULL,
    description varchar(2000),
    status varchar(24) NOT NULL CHECK (status IN ('ACTIVE','INACTIVE','ARCHIVED')),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_competency_tenant_id UNIQUE (tenant_id,id),
    CONSTRAINT uq_competency_tenant_code UNIQUE (tenant_id,code),
    CONSTRAINT fk_competency_outcome FOREIGN KEY (tenant_id,outcome_id) REFERENCES learning_outcome(tenant_id,id) ON DELETE RESTRICT
);
CREATE TABLE learning_outcome_mapping (
    id uuid PRIMARY KEY DEFAULT uuidv7(), tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    outcome_id uuid NOT NULL, target_type varchar(32) NOT NULL, target_id uuid NOT NULL, weight numeric(8,6) NOT NULL CHECK(weight>0 AND weight<=1),
    CONSTRAINT fk_mapping_outcome FOREIGN KEY (tenant_id,outcome_id) REFERENCES learning_outcome(tenant_id,id) ON DELETE CASCADE,
    CONSTRAINT uq_learning_outcome_mapping UNIQUE (tenant_id,outcome_id,target_type,target_id)
);
CREATE TABLE competency_mastery (
    id uuid PRIMARY KEY DEFAULT uuidv7(), tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    outcome_id uuid NOT NULL, competency_id uuid NOT NULL, membership_id uuid NOT NULL, mastery numeric(8,6) NOT NULL CHECK(mastery>=0 AND mastery<=1), evidence varchar(4000), updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mastery_outcome FOREIGN KEY (tenant_id,outcome_id) REFERENCES learning_outcome(tenant_id,id) ON DELETE CASCADE,
    CONSTRAINT fk_mastery_competency FOREIGN KEY (tenant_id,competency_id) REFERENCES competency(tenant_id,id) ON DELETE CASCADE,
    CONSTRAINT fk_mastery_membership FOREIGN KEY (tenant_id,membership_id) REFERENCES tenant_membership(tenant_id,id) ON DELETE CASCADE,
    CONSTRAINT uq_competency_mastery UNIQUE (tenant_id,competency_id,membership_id)
);
CREATE TABLE learning_path (
    id uuid PRIMARY KEY DEFAULT uuidv7(), tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    code varchar(80) NOT NULL, name varchar(200) NOT NULL, description varchar(2000), status varchar(24) NOT NULL CHECK(status IN ('ACTIVE','INACTIVE','ARCHIVED')), version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_learning_path_tenant_id UNIQUE(tenant_id,id), CONSTRAINT uq_learning_path_tenant_code UNIQUE(tenant_id,code)
);
CREATE TABLE learning_path_item (
    id uuid PRIMARY KEY DEFAULT uuidv7(), tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE, path_id uuid NOT NULL,
    sequence_number integer NOT NULL CHECK(sequence_number>0), item_type varchar(32) NOT NULL, item_id uuid NOT NULL, title varchar(300) NOT NULL, required boolean NOT NULL DEFAULT true,
    CONSTRAINT fk_learning_path_item_path FOREIGN KEY(tenant_id,path_id) REFERENCES learning_path(tenant_id,id) ON DELETE CASCADE,
    CONSTRAINT uq_learning_path_item_sequence UNIQUE(tenant_id,path_id,sequence_number)
);
CREATE TABLE learning_path_progress (
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE, path_id uuid NOT NULL, membership_id uuid NOT NULL,
    progress numeric(8,6) NOT NULL CHECK(progress>=0 AND progress<=1), updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(tenant_id,path_id,membership_id), CONSTRAINT fk_path_progress_path FOREIGN KEY(tenant_id,path_id) REFERENCES learning_path(tenant_id,id) ON DELETE CASCADE,
    CONSTRAINT fk_path_progress_membership FOREIGN KEY(tenant_id,membership_id) REFERENCES tenant_membership(tenant_id,id) ON DELETE CASCADE
);
CREATE TABLE credential_template (
    id uuid PRIMARY KEY DEFAULT uuidv7(), tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE, code varchar(80) NOT NULL, name varchar(200) NOT NULL, description varchar(2000), credential_type varchar(32) NOT NULL, status varchar(24) NOT NULL CHECK(status IN ('ACTIVE','INACTIVE')), version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_credential_template_tenant_id UNIQUE(tenant_id,id), CONSTRAINT uq_credential_template_tenant_code UNIQUE(tenant_id,code)
);
CREATE TABLE credential (
    id uuid PRIMARY KEY DEFAULT uuidv7(), tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE, template_id uuid NOT NULL, membership_id uuid NOT NULL,
    verification_code varchar(64) NOT NULL UNIQUE, verification_url varchar(1000) NOT NULL, qr_payload varchar(1000) NOT NULL,
    issued_at timestamptz NOT NULL, issuer_subject varchar(160) NOT NULL, reason varchar(500) NOT NULL, source_id uuid,
    status varchar(24) NOT NULL CHECK(status IN ('ISSUED','REVOKED')), revoked_at timestamptz, revocation_reason varchar(500), version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_credential_tenant_id UNIQUE(tenant_id,id), CONSTRAINT fk_credential_template FOREIGN KEY(tenant_id,template_id) REFERENCES credential_template(tenant_id,id) ON DELETE RESTRICT,
    CONSTRAINT fk_credential_membership FOREIGN KEY(tenant_id,membership_id) REFERENCES tenant_membership(tenant_id,id) ON DELETE RESTRICT
);
CREATE INDEX ix_credential_membership ON credential(tenant_id,membership_id,issued_at DESC);
CREATE TABLE mentorship (
    id uuid PRIMARY KEY DEFAULT uuidv7(), tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE, mentor_membership_id uuid NOT NULL, mentee_membership_id uuid NOT NULL,
    goal varchar(2000) NOT NULL, status varchar(24) NOT NULL CHECK(status IN ('REQUESTED','ACTIVE','PAUSED','COMPLETED','DECLINED')), created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_mentorship_tenant_id UNIQUE(tenant_id,id), CONSTRAINT fk_mentorship_mentor FOREIGN KEY(tenant_id,mentor_membership_id) REFERENCES tenant_membership(tenant_id,id) ON DELETE RESTRICT,
    CONSTRAINT fk_mentorship_mentee FOREIGN KEY(tenant_id,mentee_membership_id) REFERENCES tenant_membership(tenant_id,id) ON DELETE RESTRICT, CONSTRAINT ck_mentorship_distinct CHECK(mentor_membership_id<>mentee_membership_id)
);
CREATE INDEX ix_mentorship_mentee ON mentorship(tenant_id,mentee_membership_id,status);
CREATE TABLE survey (
    id uuid PRIMARY KEY DEFAULT uuidv7(), tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE, title varchar(240) NOT NULL, description varchar(4000), anonymous boolean NOT NULL DEFAULT false,
    opens_at timestamptz, closes_at timestamptz, status varchar(24) NOT NULL CHECK(status IN ('DRAFT','OPEN','CLOSED','ARCHIVED')), version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_survey_tenant_id UNIQUE(tenant_id,id), CONSTRAINT ck_survey_window CHECK(closes_at IS NULL OR opens_at IS NULL OR closes_at>opens_at)
);
CREATE TABLE survey_question (
    id uuid PRIMARY KEY DEFAULT uuidv7(), tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE, survey_id uuid NOT NULL, sequence_number integer NOT NULL CHECK(sequence_number>0),
    question_type varchar(32) NOT NULL, prompt varchar(2000) NOT NULL, required boolean NOT NULL DEFAULT true, options_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    CONSTRAINT fk_survey_question_survey FOREIGN KEY(tenant_id,survey_id) REFERENCES survey(tenant_id,id) ON DELETE CASCADE, CONSTRAINT uq_survey_question_sequence UNIQUE(tenant_id,survey_id,sequence_number)
);

CREATE TABLE survey_response (
    id uuid PRIMARY KEY DEFAULT uuidv7(), tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE, survey_id uuid NOT NULL, membership_id uuid, answers_json jsonb NOT NULL, submitted_at timestamptz NOT NULL,
    CONSTRAINT uq_survey_response_tenant_id UNIQUE(tenant_id,id), CONSTRAINT fk_survey_response_survey FOREIGN KEY(tenant_id,survey_id) REFERENCES survey(tenant_id,id) ON DELETE CASCADE,
    CONSTRAINT fk_survey_response_membership FOREIGN KEY(tenant_id,membership_id) REFERENCES tenant_membership(tenant_id,id) ON DELETE RESTRICT
);
CREATE UNIQUE INDEX uq_survey_response_member ON survey_response(tenant_id,survey_id,membership_id) WHERE membership_id IS NOT NULL;
CREATE TABLE feedback (
    id uuid PRIMARY KEY DEFAULT uuidv7(), tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE, target_type varchar(32) NOT NULL, target_id uuid NOT NULL, author_membership_id uuid NOT NULL,
    rating smallint NOT NULL CHECK(rating BETWEEN 1 AND 5), comments varchar(4000) NOT NULL, created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, status varchar(24) NOT NULL CHECK(status IN ('SUBMITTED','REVIEWED','HIDDEN')),
    CONSTRAINT uq_feedback_tenant_id UNIQUE(tenant_id,id), CONSTRAINT fk_feedback_author FOREIGN KEY(tenant_id,author_membership_id) REFERENCES tenant_membership(tenant_id,id) ON DELETE RESTRICT
);
CREATE INDEX ix_feedback_target ON feedback(tenant_id,target_type,target_id,created_at DESC);
CREATE INDEX ix_learning_mapping_target ON learning_outcome_mapping(tenant_id,target_type,target_id);
CREATE INDEX ix_learning_mastery_outcome_member ON competency_mastery(tenant_id,outcome_id,membership_id);

INSERT INTO role_permission (role_id, permission_key)
SELECT rd.id, x.permission_key FROM role_definition rd JOIN (VALUES
 ('ORGANIZATION_OWNER','LEARNING_OUTCOMES_VIEW'),('ORGANIZATION_OWNER','LEARNING_OUTCOMES_MANAGE'),('ORGANIZATION_OWNER','CREDENTIALS_VIEW'),('ORGANIZATION_OWNER','CREDENTIALS_MANAGE'),('ORGANIZATION_OWNER','CREDENTIALS_VERIFY'),('ORGANIZATION_OWNER','MENTORING_VIEW'),('ORGANIZATION_OWNER','MENTORING_MANAGE'),('ORGANIZATION_OWNER','SURVEYS_VIEW'),('ORGANIZATION_OWNER','SURVEYS_MANAGE'),('ORGANIZATION_OWNER','FEEDBACK_VIEW'),('ORGANIZATION_OWNER','FEEDBACK_MANAGE'),
 ('ORGANIZATION_ADMINISTRATOR','LEARNING_OUTCOMES_VIEW'),('ORGANIZATION_ADMINISTRATOR','LEARNING_OUTCOMES_MANAGE'),('ORGANIZATION_ADMINISTRATOR','CREDENTIALS_VIEW'),('ORGANIZATION_ADMINISTRATOR','CREDENTIALS_MANAGE'),('ORGANIZATION_ADMINISTRATOR','CREDENTIALS_VERIFY'),('ORGANIZATION_ADMINISTRATOR','MENTORING_VIEW'),('ORGANIZATION_ADMINISTRATOR','MENTORING_MANAGE'),('ORGANIZATION_ADMINISTRATOR','SURVEYS_VIEW'),('ORGANIZATION_ADMINISTRATOR','SURVEYS_MANAGE'),('ORGANIZATION_ADMINISTRATOR','FEEDBACK_VIEW'),('ORGANIZATION_ADMINISTRATOR','FEEDBACK_MANAGE'),
 ('ACADEMIC_ADMINISTRATOR','LEARNING_OUTCOMES_VIEW'),('ACADEMIC_ADMINISTRATOR','LEARNING_OUTCOMES_MANAGE'),('ACADEMIC_ADMINISTRATOR','CREDENTIALS_VIEW'),('ACADEMIC_ADMINISTRATOR','CREDENTIALS_MANAGE'),('ACADEMIC_ADMINISTRATOR','CREDENTIALS_VERIFY'),('ACADEMIC_ADMINISTRATOR','MENTORING_VIEW'),('ACADEMIC_ADMINISTRATOR','MENTORING_MANAGE'),('ACADEMIC_ADMINISTRATOR','SURVEYS_VIEW'),('ACADEMIC_ADMINISTRATOR','SURVEYS_MANAGE'),('ACADEMIC_ADMINISTRATOR','FEEDBACK_VIEW'),('ACADEMIC_ADMINISTRATOR','FEEDBACK_MANAGE'),
 ('TEACHER','LEARNING_OUTCOMES_VIEW'),('TEACHER','CREDENTIALS_VIEW'),('TEACHER','CREDENTIALS_VERIFY'),('TEACHER','MENTORING_VIEW'),('TEACHER','SURVEYS_VIEW'),('TEACHER','FEEDBACK_VIEW'),
 ('STUDENT','LEARNING_OUTCOMES_VIEW'),('STUDENT','CREDENTIALS_VIEW'),('STUDENT','CREDENTIALS_VERIFY'),('STUDENT','MENTORING_VIEW'),('STUDENT','SURVEYS_VIEW'),('STUDENT','FEEDBACK_VIEW'),
 ('MENTOR','LEARNING_OUTCOMES_VIEW'),('MENTOR','CREDENTIALS_VIEW'),('MENTOR','CREDENTIALS_VERIFY'),('MENTOR','MENTORING_VIEW'),('MENTOR','MENTORING_MANAGE'),('MENTOR','SURVEYS_VIEW'),('MENTOR','FEEDBACK_VIEW')
) AS x(system_key,permission_key) ON rd.system_key=x.system_key ON CONFLICT DO NOTHING;
