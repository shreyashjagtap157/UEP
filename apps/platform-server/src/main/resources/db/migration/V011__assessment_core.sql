CREATE TABLE question (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenant(id),
    title varchar(200) NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_question_tenant_id UNIQUE (tenant_id, id)
);

CREATE INDEX idx_question_tenant_created ON question(tenant_id, created_at DESC);

CREATE TABLE question_version (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    question_id uuid NOT NULL,
    version_number integer NOT NULL,
    type varchar(32) NOT NULL,
    difficulty varchar(32) NOT NULL,
    language varchar(64),
    payload_json text NOT NULL,
    positive_marks integer NOT NULL,
    negative_marks integer NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_question_version_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_question_version_number UNIQUE (tenant_id, question_id, version_number),
    CONSTRAINT fk_question_version_question FOREIGN KEY (tenant_id, question_id) REFERENCES question(tenant_id, id),
    CONSTRAINT ck_question_version_marks CHECK (positive_marks > 0 AND negative_marks >= 0),
    CONSTRAINT ck_question_version_number CHECK (version_number > 0)
);
CREATE INDEX idx_question_version_question ON question_version(tenant_id, question_id, version_number DESC);

CREATE TABLE assessment (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenant(id),
    title varchar(240) NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_assessment_tenant_id UNIQUE (tenant_id, id)
);
CREATE INDEX idx_assessment_tenant_created ON assessment(tenant_id, created_at DESC);

CREATE TABLE assessment_version (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    assessment_id uuid NOT NULL,
    version_number integer NOT NULL,
    duration_seconds integer,
    max_attempts integer NOT NULL,
    total_marks integer NOT NULL,
    pass_marks integer NOT NULL,
    shuffle_questions boolean NOT NULL,
    allow_backtracking boolean NOT NULL,
    available_from timestamptz NOT NULL,
    available_until timestamptz NOT NULL,
    settings_json text,
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_assessment_version_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_assessment_version_number UNIQUE (tenant_id, assessment_id, version_number),
    CONSTRAINT fk_assessment_version_assessment FOREIGN KEY (tenant_id, assessment_id) REFERENCES assessment(tenant_id, id),
    CONSTRAINT ck_assessment_version_window CHECK (available_from < available_until),
    CONSTRAINT ck_assessment_version_attempts CHECK (max_attempts > 0),
    CONSTRAINT ck_assessment_version_marks CHECK (total_marks > 0 AND pass_marks >= 0 AND pass_marks <= total_marks),
    CONSTRAINT ck_assessment_version_duration CHECK (duration_seconds IS NULL OR duration_seconds >= 30)
);
CREATE INDEX idx_assessment_version_assessment ON assessment_version(tenant_id, assessment_id, version_number DESC);

CREATE TABLE assessment_question (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    assessment_version_id uuid NOT NULL,
    question_version_id uuid NOT NULL,
    ordinal integer NOT NULL,
    marks integer NOT NULL,
    CONSTRAINT uq_assessment_question_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_assessment_question_question UNIQUE (tenant_id, assessment_version_id, question_version_id),
    CONSTRAINT uq_assessment_question_ordinal UNIQUE (tenant_id, assessment_version_id, ordinal),
    CONSTRAINT fk_assessment_question_version FOREIGN KEY (tenant_id, assessment_version_id) REFERENCES assessment_version(tenant_id, id),
    CONSTRAINT fk_assessment_question_question_version FOREIGN KEY (tenant_id, question_version_id) REFERENCES question_version(tenant_id, id),
    CONSTRAINT ck_assessment_question_ordinal CHECK (ordinal > 0),
    CONSTRAINT ck_assessment_question_marks CHECK (marks > 0)
);
CREATE INDEX idx_assessment_question_assessment ON assessment_question(tenant_id, assessment_version_id, ordinal);

CREATE TABLE assessment_batch (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    assessment_version_id uuid NOT NULL,
    batch_id uuid NOT NULL,
    assigned_at timestamptz NOT NULL,
    CONSTRAINT uq_assessment_batch_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_assessment_batch_assignment UNIQUE (tenant_id, assessment_version_id, batch_id),
    CONSTRAINT fk_assessment_batch_version FOREIGN KEY (tenant_id, assessment_version_id) REFERENCES assessment_version(tenant_id, id),
    CONSTRAINT fk_assessment_batch_batch FOREIGN KEY (tenant_id, batch_id) REFERENCES batch(tenant_id, id)
);
CREATE INDEX idx_assessment_batch_batch ON assessment_batch(tenant_id, batch_id);

CREATE TABLE attempt (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    assessment_version_id uuid NOT NULL,
    membership_id uuid NOT NULL,
    started_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    submitted_at timestamptz,
    status varchar(20) NOT NULL,
    attempt_number integer NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_attempt_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_attempt_assessment_version FOREIGN KEY (tenant_id, assessment_version_id) REFERENCES assessment_version(tenant_id, id),
    CONSTRAINT fk_attempt_membership FOREIGN KEY (tenant_id, membership_id) REFERENCES membership(tenant_id, id),
    CONSTRAINT uq_attempt_number UNIQUE (tenant_id, assessment_version_id, membership_id, attempt_number),
    CONSTRAINT ck_attempt_window CHECK (started_at < expires_at),
    CONSTRAINT ck_attempt_number CHECK (attempt_number > 0)
);
CREATE INDEX idx_attempt_membership ON attempt(tenant_id, membership_id, started_at DESC);
CREATE INDEX idx_attempt_assessment ON attempt(tenant_id, assessment_version_id, membership_id, attempt_number);

CREATE TABLE answer (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    attempt_id uuid NOT NULL,
    assessment_question_id uuid NOT NULL,
    payload_json text NOT NULL,
    server_sequence bigint NOT NULL,
    saved_at timestamptz NOT NULL,
    idempotency_key varchar(120) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_answer_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_answer_question UNIQUE (tenant_id, attempt_id, assessment_question_id),
    CONSTRAINT uq_answer_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT fk_answer_attempt FOREIGN KEY (tenant_id, attempt_id) REFERENCES attempt(tenant_id, id),
    CONSTRAINT fk_answer_question FOREIGN KEY (tenant_id, assessment_question_id) REFERENCES assessment_question(tenant_id, id)
);
CREATE INDEX idx_answer_attempt_sequence ON answer(tenant_id, attempt_id, server_sequence);
