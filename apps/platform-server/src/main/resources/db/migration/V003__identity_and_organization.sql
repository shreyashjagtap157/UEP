CREATE TABLE user_account (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    oidc_subject varchar(160) NOT NULL UNIQUE,
    email varchar(320),
    display_name varchar(200) NOT NULL,
    status varchar(32) NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0
);

CREATE TABLE branch (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    code varchar(64) NOT NULL,
    display_name varchar(200) NOT NULL,
    timezone varchar(80) NOT NULL,
    status varchar(32) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE', 'CLOSED')),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_branch_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_branch_tenant_id UNIQUE (tenant_id, id)
);

CREATE TABLE organization_settings (
    tenant_id uuid PRIMARY KEY REFERENCES tenant(id) ON DELETE CASCADE,
    default_timezone varchar(80) NOT NULL DEFAULT 'UTC',
    default_locale varchar(35) NOT NULL DEFAULT 'en',
    week_starts_on smallint NOT NULL DEFAULT 1 CHECK (week_starts_on BETWEEN 1 AND 7),
    support_email varchar(320),
    support_url varchar(1000),
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0
);

INSERT INTO organization_settings (tenant_id)
SELECT id FROM tenant
ON CONFLICT (tenant_id) DO NOTHING;

CREATE TABLE tenant_membership (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES user_account(id) ON DELETE RESTRICT,
    primary_branch_id uuid,
    status varchar(32) NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ENDED')),
    external_reference varchar(160),
    joined_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_membership_tenant_user UNIQUE (tenant_id, user_id),
    CONSTRAINT uq_membership_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_membership_primary_branch FOREIGN KEY (tenant_id, primary_branch_id)
        REFERENCES branch(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_membership_ended CHECK ((status = 'ENDED' AND ended_at IS NOT NULL) OR status <> 'ENDED')
);

CREATE TABLE role_definition (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    system_key varchar(64),
    name varchar(120) NOT NULL,
    description varchar(500),
    system_managed boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_role_tenant_system_key UNIQUE (tenant_id, system_key),
    CONSTRAINT uq_role_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_role_system_key CHECK ((system_managed AND system_key IS NOT NULL) OR (NOT system_managed AND system_key IS NULL))
);

CREATE UNIQUE INDEX uq_role_tenant_name_ci ON role_definition (tenant_id, lower(name));

CREATE TABLE role_permission (
    role_id uuid NOT NULL REFERENCES role_definition(id) ON DELETE CASCADE,
    permission_key varchar(80) NOT NULL,
    PRIMARY KEY (role_id, permission_key)
);

CREATE TABLE role_assignment (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    membership_id uuid NOT NULL,
    role_id uuid NOT NULL,
    scope_kind varchar(32) NOT NULL DEFAULT 'TENANT' CHECK (scope_kind IN ('TENANT', 'BRANCH')),
    scope_id uuid,
    assigned_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by_subject varchar(160) NOT NULL,
    CONSTRAINT fk_assignment_membership FOREIGN KEY (tenant_id, membership_id)
        REFERENCES tenant_membership(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_assignment_role FOREIGN KEY (tenant_id, role_id)
        REFERENCES role_definition(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_assignment_branch FOREIGN KEY (tenant_id, scope_id)
        REFERENCES branch(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_assignment_scope CHECK (
        (scope_kind = 'TENANT' AND scope_id IS NULL) OR
        (scope_kind = 'BRANCH' AND scope_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_assignment_tenant_scope
    ON role_assignment (tenant_id, membership_id, role_id)
    WHERE scope_kind = 'TENANT';
CREATE UNIQUE INDEX uq_assignment_branch_scope
    ON role_assignment (tenant_id, membership_id, role_id, scope_id)
    WHERE scope_kind = 'BRANCH';

CREATE TABLE platform_session (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    membership_id uuid NOT NULL,
    oidc_session_id varchar(200) NOT NULL,
    started_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at timestamptz,
    revoked_at timestamptz,
    revoked_by_subject varchar(160),
    revocation_reason varchar(500),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_session_membership FOREIGN KEY (tenant_id, membership_id)
        REFERENCES tenant_membership(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_session_oidc UNIQUE (tenant_id, membership_id, oidc_session_id),
    CONSTRAINT ck_session_revocation CHECK (
        (revoked_at IS NULL AND revoked_by_subject IS NULL) OR
        (revoked_at IS NOT NULL AND revoked_by_subject IS NOT NULL)
    )
);

CREATE INDEX ix_user_account_email_ci ON user_account (lower(email)) WHERE email IS NOT NULL;
CREATE INDEX ix_membership_tenant_status ON tenant_membership (tenant_id, status);
CREATE INDEX ix_membership_branch ON tenant_membership (tenant_id, primary_branch_id) WHERE primary_branch_id IS NOT NULL;
CREATE INDEX ix_role_assignment_member ON role_assignment (tenant_id, membership_id);
CREATE INDEX ix_role_permission_key ON role_permission (permission_key);
CREATE INDEX ix_platform_session_member_seen ON platform_session (tenant_id, membership_id, last_seen_at DESC);
CREATE INDEX ix_platform_session_active ON platform_session (tenant_id, oidc_session_id) WHERE revoked_at IS NULL;

-- Seed the built-in role catalog for tenants that already exist when this migration runs.
INSERT INTO role_definition (id, tenant_id, system_key, name, description, system_managed)
SELECT uuidv7(), t.id, r.system_key, r.name, r.description, true
FROM tenant t
CROSS JOIN (VALUES
    ('ORGANIZATION_OWNER', 'Organization Owner', 'Full tenant administrative authority.'),
    ('ORGANIZATION_ADMINISTRATOR', 'Organization Administrator', 'Tenant-wide administrative authority.'),
    ('BRANCH_ADMINISTRATOR', 'Branch Administrator', 'Branch-oriented administrative visibility.'),
    ('ACADEMIC_ADMINISTRATOR', 'Academic Administrator', 'Academic administration identity.'),
    ('EXAM_CONTROLLER', 'Exam Controller', 'Assessment governance identity.'),
    ('FINANCE_ADMINISTRATOR', 'Finance Administrator', 'Finance administration identity.'),
    ('TEACHER', 'Teacher', 'Teaching identity.'),
    ('EVALUATOR', 'Evaluator', 'Assessment evaluator identity.'),
    ('TEACHING_ASSISTANT', 'Teaching Assistant', 'Teaching assistant identity.'),
    ('MENTOR', 'Mentor', 'Mentor identity.'),
    ('STUDENT', 'Student', 'Learner identity.'),
    ('GUARDIAN', 'Guardian', 'Guardian identity.'),
    ('SUPPORT_OPERATOR', 'Support Operator', 'Tenant support visibility without mutation privileges.'),
    ('AUDITOR', 'Auditor', 'Read-only audit and governance visibility.')
) AS r(system_key, name, description)
ON CONFLICT (tenant_id, system_key) DO NOTHING;

INSERT INTO role_permission (role_id, permission_key)
SELECT rd.id, rp.permission_key
FROM role_definition rd
JOIN (VALUES
    ('ORGANIZATION_OWNER', 'ORGANIZATION_VIEW'),
    ('ORGANIZATION_OWNER', 'ORGANIZATION_MANAGE'),
    ('ORGANIZATION_OWNER', 'BRANCHES_VIEW'),
    ('ORGANIZATION_OWNER', 'BRANCHES_MANAGE'),
    ('ORGANIZATION_OWNER', 'USERS_VIEW'),
    ('ORGANIZATION_OWNER', 'USERS_MANAGE'),
    ('ORGANIZATION_OWNER', 'ROLES_VIEW'),
    ('ORGANIZATION_OWNER', 'ROLES_MANAGE'),
    ('ORGANIZATION_OWNER', 'ROLES_ASSIGN'),
    ('ORGANIZATION_OWNER', 'SESSIONS_VIEW'),
    ('ORGANIZATION_OWNER', 'SESSIONS_MANAGE'),
    ('ORGANIZATION_OWNER', 'AUDIT_VIEW'),
    ('ORGANIZATION_ADMINISTRATOR', 'ORGANIZATION_VIEW'),
    ('ORGANIZATION_ADMINISTRATOR', 'ORGANIZATION_MANAGE'),
    ('ORGANIZATION_ADMINISTRATOR', 'BRANCHES_VIEW'),
    ('ORGANIZATION_ADMINISTRATOR', 'BRANCHES_MANAGE'),
    ('ORGANIZATION_ADMINISTRATOR', 'USERS_VIEW'),
    ('ORGANIZATION_ADMINISTRATOR', 'USERS_MANAGE'),
    ('ORGANIZATION_ADMINISTRATOR', 'ROLES_VIEW'),
    ('ORGANIZATION_ADMINISTRATOR', 'ROLES_MANAGE'),
    ('ORGANIZATION_ADMINISTRATOR', 'ROLES_ASSIGN'),
    ('ORGANIZATION_ADMINISTRATOR', 'SESSIONS_VIEW'),
    ('ORGANIZATION_ADMINISTRATOR', 'SESSIONS_MANAGE'),
    ('ORGANIZATION_ADMINISTRATOR', 'AUDIT_VIEW'),
    ('BRANCH_ADMINISTRATOR', 'ORGANIZATION_VIEW'),
    ('BRANCH_ADMINISTRATOR', 'BRANCHES_VIEW'),
    ('BRANCH_ADMINISTRATOR', 'USERS_VIEW'),
    ('BRANCH_ADMINISTRATOR', 'ROLES_VIEW'),
    ('BRANCH_ADMINISTRATOR', 'SESSIONS_VIEW'),
    ('ACADEMIC_ADMINISTRATOR', 'ORGANIZATION_VIEW'),
    ('ACADEMIC_ADMINISTRATOR', 'BRANCHES_VIEW'),
    ('ACADEMIC_ADMINISTRATOR', 'USERS_VIEW'),
    ('EXAM_CONTROLLER', 'ORGANIZATION_VIEW'),
    ('EXAM_CONTROLLER', 'BRANCHES_VIEW'),
    ('EXAM_CONTROLLER', 'USERS_VIEW'),
    ('FINANCE_ADMINISTRATOR', 'ORGANIZATION_VIEW'),
    ('FINANCE_ADMINISTRATOR', 'BRANCHES_VIEW'),
    ('FINANCE_ADMINISTRATOR', 'USERS_VIEW'),
    ('TEACHER', 'ORGANIZATION_VIEW'),
    ('TEACHER', 'BRANCHES_VIEW'),
    ('TEACHER', 'USERS_VIEW'),
    ('EVALUATOR', 'ORGANIZATION_VIEW'),
    ('EVALUATOR', 'BRANCHES_VIEW'),
    ('EVALUATOR', 'USERS_VIEW'),
    ('TEACHING_ASSISTANT', 'ORGANIZATION_VIEW'),
    ('TEACHING_ASSISTANT', 'BRANCHES_VIEW'),
    ('TEACHING_ASSISTANT', 'USERS_VIEW'),
    ('MENTOR', 'ORGANIZATION_VIEW'),
    ('MENTOR', 'BRANCHES_VIEW'),
    ('MENTOR', 'USERS_VIEW'),
    ('STUDENT', 'ORGANIZATION_VIEW'),
    ('STUDENT', 'BRANCHES_VIEW'),
    ('GUARDIAN', 'ORGANIZATION_VIEW'),
    ('GUARDIAN', 'BRANCHES_VIEW'),
    ('SUPPORT_OPERATOR', 'ORGANIZATION_VIEW'),
    ('SUPPORT_OPERATOR', 'BRANCHES_VIEW'),
    ('SUPPORT_OPERATOR', 'USERS_VIEW'),
    ('SUPPORT_OPERATOR', 'ROLES_VIEW'),
    ('SUPPORT_OPERATOR', 'SESSIONS_VIEW'),
    ('AUDITOR', 'ORGANIZATION_VIEW'),
    ('AUDITOR', 'BRANCHES_VIEW'),
    ('AUDITOR', 'USERS_VIEW'),
    ('AUDITOR', 'ROLES_VIEW'),
    ('AUDITOR', 'SESSIONS_VIEW'),
    ('AUDITOR', 'AUDIT_VIEW')
) AS rp(system_key, permission_key)
ON rd.system_key = rp.system_key
ON CONFLICT (role_id, permission_key) DO NOTHING;
