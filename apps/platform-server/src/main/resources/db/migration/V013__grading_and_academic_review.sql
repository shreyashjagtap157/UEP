CREATE TABLE grade_revision (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    attempt_id uuid NOT NULL,
    revision_number integer NOT NULL,
    status varchar(20) NOT NULL,
    source varchar(24) NOT NULL,
    awarded_marks integer NOT NULL,
    max_marks integer NOT NULL,
    created_at timestamptz NOT NULL,
    actor_subject varchar(255) NOT NULL,
    rationale_json text NOT NULL,
    CONSTRAINT uq_grade_revision_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_grade_revision_number UNIQUE (tenant_id, attempt_id, revision_number),
    CONSTRAINT fk_grade_revision_attempt FOREIGN KEY (tenant_id, attempt_id) REFERENCES attempt(tenant_id, id),
    CONSTRAINT ck_grade_revision_number CHECK (revision_number > 0),
    CONSTRAINT ck_grade_revision_max CHECK (max_marks > 0),
    CONSTRAINT ck_grade_revision_score CHECK (awarded_marks <= max_marks)
);
CREATE INDEX idx_grade_revision_attempt ON grade_revision(tenant_id, attempt_id, revision_number DESC);

CREATE TABLE grade_item (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    grade_revision_id uuid NOT NULL,
    answer_id uuid NOT NULL,
    assessment_question_id uuid NOT NULL,
    max_marks integer NOT NULL,
    negative_marks integer NOT NULL,
    system_score integer NOT NULL,
    teacher_score integer,
    final_score integer NOT NULL,
    created_at timestamptz NOT NULL,
    explanation_json text NOT NULL,
    rubric_json text NOT NULL,
    CONSTRAINT uq_grade_item_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_grade_item_answer UNIQUE (tenant_id, grade_revision_id, answer_id),
    CONSTRAINT fk_grade_item_revision FOREIGN KEY (tenant_id, grade_revision_id) REFERENCES grade_revision(tenant_id, id),
    CONSTRAINT fk_grade_item_answer FOREIGN KEY (tenant_id, answer_id) REFERENCES answer(tenant_id, id),
    CONSTRAINT fk_grade_item_question FOREIGN KEY (tenant_id, assessment_question_id) REFERENCES assessment_question(tenant_id, id),
    CONSTRAINT ck_grade_item_marks CHECK (max_marks > 0 AND negative_marks >= 0),
    CONSTRAINT ck_grade_item_system_score CHECK (system_score >= -negative_marks AND system_score <= max_marks),
    CONSTRAINT ck_grade_item_final_score CHECK (final_score >= -negative_marks AND final_score <= max_marks),
    CONSTRAINT ck_grade_item_teacher_score CHECK (teacher_score IS NULL OR (teacher_score >= -negative_marks AND teacher_score <= max_marks))
);
CREATE INDEX idx_grade_item_revision ON grade_item(tenant_id, grade_revision_id);
CREATE INDEX idx_grade_item_answer ON grade_item(tenant_id, answer_id);

CREATE TABLE review_case (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    attempt_id uuid NOT NULL,
    membership_id uuid NOT NULL,
    target_answer_id uuid,
    type varchar(24) NOT NULL,
    status varchar(20) NOT NULL,
    subject text NOT NULL,
    opening_argument text NOT NULL,
    version integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_review_case_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_review_case_attempt FOREIGN KEY (tenant_id, attempt_id) REFERENCES attempt(tenant_id, id),
    CONSTRAINT fk_review_case_membership FOREIGN KEY (tenant_id, membership_id) REFERENCES membership(tenant_id, id),
    CONSTRAINT fk_review_case_answer FOREIGN KEY (tenant_id, target_answer_id) REFERENCES answer(tenant_id, id),
    CONSTRAINT ck_review_case_version CHECK (version >= 0)
);
CREATE INDEX idx_review_case_attempt ON review_case(tenant_id, attempt_id, created_at DESC);

CREATE TABLE review_comment (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    review_case_id uuid NOT NULL,
    actor_subject varchar(255) NOT NULL,
    body text NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_review_comment_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_review_comment_case FOREIGN KEY (tenant_id, review_case_id) REFERENCES review_case(tenant_id, id)
);
CREATE INDEX idx_review_comment_case ON review_comment(tenant_id, review_case_id, created_at ASC);

CREATE TABLE review_impact (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    review_case_id uuid NOT NULL,
    previous_score integer NOT NULL,
    projected_score integer NOT NULL,
    affected_rule varchar(120) NOT NULL,
    analysis_json text NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_review_impact_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_review_impact_case FOREIGN KEY (tenant_id, review_case_id) REFERENCES review_case(tenant_id, id)
);
CREATE INDEX idx_review_impact_case ON review_impact(tenant_id, review_case_id, created_at DESC);

CREATE TABLE answer_revision (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    review_case_id uuid NOT NULL,
    answer_id uuid NOT NULL,
    revision_number integer NOT NULL,
    status varchar(16) NOT NULL,
    proposed_payload_json text NOT NULL,
    actor_subject varchar(255) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_answer_revision_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_answer_revision_number UNIQUE (tenant_id, review_case_id, answer_id, revision_number),
    CONSTRAINT fk_answer_revision_case FOREIGN KEY (tenant_id, review_case_id) REFERENCES review_case(tenant_id, id),
    CONSTRAINT fk_answer_revision_answer FOREIGN KEY (tenant_id, answer_id) REFERENCES answer(tenant_id, id),
    CONSTRAINT ck_answer_revision_number CHECK (revision_number > 0)
);
CREATE INDEX idx_answer_revision_case ON answer_revision(tenant_id, review_case_id, revision_number DESC);
