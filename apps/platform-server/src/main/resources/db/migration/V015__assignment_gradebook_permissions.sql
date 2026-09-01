CREATE TABLE assignment (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, title varchar(240) NOT NULL, instructions text,
 status varchar(20) NOT NULL, max_points integer NOT NULL, weight_basis_points integer NOT NULL, due_at timestamptz NOT NULL,
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL, version bigint NOT NULL DEFAULT 0,
 CONSTRAINT uq_assignment_tenant_id UNIQUE(tenant_id,id), CONSTRAINT ck_assignment_points CHECK(max_points>0),
 CONSTRAINT ck_assignment_weight CHECK(weight_basis_points BETWEEN 0 AND 10000)
);
CREATE INDEX idx_assignment_tenant_due ON assignment(tenant_id,due_at);
CREATE TABLE assignment_batch (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, assignment_id uuid NOT NULL, batch_id uuid NOT NULL, assigned_at timestamptz NOT NULL,
 CONSTRAINT uq_assignment_batch_tenant_id UNIQUE(tenant_id,id), CONSTRAINT uq_assignment_batch_link UNIQUE(tenant_id,assignment_id,batch_id),
 CONSTRAINT fk_assignment_batch_assignment FOREIGN KEY(tenant_id,assignment_id) REFERENCES assignment(tenant_id,id),
 CONSTRAINT fk_assignment_batch_batch FOREIGN KEY(tenant_id,batch_id) REFERENCES batch(tenant_id,id)
);
CREATE INDEX idx_assignment_batch_batch ON assignment_batch(tenant_id,batch_id);
CREATE TABLE assignment_submission (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, assignment_id uuid NOT NULL, membership_id uuid NOT NULL, attempt_number integer NOT NULL,
 status varchar(20) NOT NULL, grade_status varchar(20) NOT NULL, text_body text, resource_id uuid, submitted_at timestamptz,
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL, awarded_points integer, grader_feedback text, version bigint NOT NULL DEFAULT 0,
 CONSTRAINT uq_assignment_submission_tenant_id UNIQUE(tenant_id,id),
 CONSTRAINT uq_assignment_submission_attempt UNIQUE(tenant_id,assignment_id,membership_id,attempt_number),
 CONSTRAINT fk_assignment_submission_assignment FOREIGN KEY(tenant_id,assignment_id) REFERENCES assignment(tenant_id,id),
 CONSTRAINT fk_assignment_submission_membership FOREIGN KEY(tenant_id,membership_id) REFERENCES membership(tenant_id,id),
 CONSTRAINT ck_assignment_submission_attempt CHECK(attempt_number>0),
 CONSTRAINT ck_assignment_submission_points CHECK(awarded_points IS NULL OR awarded_points>=0)
);
CREATE INDEX idx_assignment_submission_assignment ON assignment_submission(tenant_id,assignment_id,submitted_at DESC);
CREATE INDEX idx_assignment_submission_membership ON assignment_submission(tenant_id,membership_id,submitted_at DESC);
CREATE TABLE assignment_rubric (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, assignment_id uuid NOT NULL, title varchar(200) NOT NULL, criteria_json text NOT NULL,
 created_at timestamptz NOT NULL, version bigint NOT NULL DEFAULT 0, CONSTRAINT uq_assignment_rubric_tenant_id UNIQUE(tenant_id,id),
 CONSTRAINT uq_assignment_rubric_assignment UNIQUE(tenant_id,assignment_id),
 CONSTRAINT fk_assignment_rubric_assignment FOREIGN KEY(tenant_id,assignment_id) REFERENCES assignment(tenant_id,id)
);

INSERT INTO role_permission (role_id, permission_key)
SELECT rd.id, grant_map.permission_key FROM role_definition rd JOIN (VALUES
 ('ORGANIZATION_OWNER','ASSIGNMENTS_VIEW'),('ORGANIZATION_OWNER','ASSIGNMENTS_MANAGE'),('ORGANIZATION_OWNER','ASSIGNMENTS_TAKE'),('ORGANIZATION_OWNER','GRADEBOOK_VIEW'),('ORGANIZATION_OWNER','GRADEBOOK_MANAGE'),
 ('ORGANIZATION_ADMINISTRATOR','ASSIGNMENTS_VIEW'),('ORGANIZATION_ADMINISTRATOR','ASSIGNMENTS_MANAGE'),('ORGANIZATION_ADMINISTRATOR','GRADEBOOK_VIEW'),('ORGANIZATION_ADMINISTRATOR','GRADEBOOK_MANAGE'),
 ('BRANCH_ADMINISTRATOR','ASSIGNMENTS_VIEW'),('BRANCH_ADMINISTRATOR','ASSIGNMENTS_MANAGE'),('BRANCH_ADMINISTRATOR','GRADEBOOK_VIEW'),
 ('ACADEMIC_ADMINISTRATOR','ASSIGNMENTS_VIEW'),('ACADEMIC_ADMINISTRATOR','ASSIGNMENTS_MANAGE'),('ACADEMIC_ADMINISTRATOR','GRADEBOOK_VIEW'),('ACADEMIC_ADMINISTRATOR','GRADEBOOK_MANAGE'),
 ('TEACHER','ASSIGNMENTS_VIEW'),('TEACHER','ASSIGNMENTS_MANAGE'),('TEACHER','ASSIGNMENTS_TAKE'),('TEACHER','GRADEBOOK_VIEW'),
 ('TEACHING_ASSISTANT','ASSIGNMENTS_VIEW'),('TEACHING_ASSISTANT','ASSIGNMENTS_MANAGE'),('TEACHING_ASSISTANT','ASSIGNMENTS_TAKE'),('TEACHING_ASSISTANT','GRADEBOOK_VIEW'),
 ('MENTOR','ASSIGNMENTS_VIEW'),('MENTOR','ASSIGNMENTS_TAKE'),('MENTOR','GRADEBOOK_VIEW'),
 ('EVALUATOR','ASSIGNMENTS_VIEW'),('EVALUATOR','ASSIGNMENTS_TAKE'),('EVALUATOR','GRADEBOOK_VIEW'),
 ('STUDENT','ASSIGNMENTS_VIEW'),('STUDENT','ASSIGNMENTS_TAKE'),('STUDENT','GRADEBOOK_VIEW'),
 ('GUARDIAN','ASSIGNMENTS_VIEW'),('GUARDIAN','GRADEBOOK_VIEW')
) AS grant_map(system_key,permission_key) ON rd.system_key=grant_map.system_key ON CONFLICT(role_id,permission_key) DO NOTHING;
