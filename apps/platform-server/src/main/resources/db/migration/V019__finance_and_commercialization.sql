ALTER TABLE subscription
    ADD COLUMN plan_code varchar(64) NOT NULL DEFAULT 'TRIAL',
    ADD COLUMN currency varchar(3) NOT NULL DEFAULT 'USD',
    ADD COLUMN recurring_amount numeric(19,2) NOT NULL DEFAULT 0,
    ADD COLUMN auto_renew boolean NOT NULL DEFAULT false,
    ADD COLUMN trial_ends_at timestamptz;

CREATE TABLE institution_fee (
    id uuid PRIMARY KEY DEFAULT uuidv7(), tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    name varchar(120) NOT NULL, amount numeric(19,2) NOT NULL CHECK (amount >= 0), currency varchar(3) NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('ACTIVE','INACTIVE')), created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0, CONSTRAINT uq_fee_tenant_name UNIQUE (tenant_id, name)
);
CREATE TABLE invoice (
    id uuid PRIMARY KEY DEFAULT uuidv7(), tenant_id uuid NOT NULL, membership_id uuid NOT NULL, invoice_number varchar(80) NOT NULL UNIQUE,
    total_amount numeric(19,2) NOT NULL CHECK (total_amount >= 0), paid_amount numeric(19,2) NOT NULL DEFAULT 0 CHECK (paid_amount >= 0), currency varchar(3) NOT NULL,
    status varchar(24) NOT NULL CHECK (status IN ('DRAFT','ISSUED','PARTIALLY_PAID','PAID','VOID','OVERDUE')), issued_on date, due_on date NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_invoice_membership FOREIGN KEY (tenant_id,membership_id) REFERENCES tenant_membership(tenant_id,id) ON DELETE RESTRICT,
    CONSTRAINT ck_invoice_paid_total CHECK (paid_amount <= total_amount)
);
CREATE INDEX ix_invoice_tenant_status_due ON invoice(tenant_id,status,due_on);
CREATE TABLE fee_installment (
    id uuid PRIMARY KEY DEFAULT uuidv7(), tenant_id uuid NOT NULL, invoice_id uuid NOT NULL, sequence_no int NOT NULL CHECK(sequence_no > 0),
    amount numeric(19,2) NOT NULL CHECK(amount > 0), due_on date NOT NULL, status varchar(20) NOT NULL CHECK(status IN ('SCHEDULED','DUE','PAID','WAIVED')), version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_installment_invoice FOREIGN KEY (tenant_id,invoice_id) REFERENCES invoice(tenant_id,id) ON DELETE CASCADE,
    CONSTRAINT uq_installment_invoice_sequence UNIQUE (tenant_id,invoice_id,sequence_no)
);
CREATE TABLE payment (
    id uuid PRIMARY KEY DEFAULT uuidv7(), tenant_id uuid NOT NULL, invoice_id uuid NOT NULL, membership_id uuid NOT NULL,
    amount numeric(19,2) NOT NULL CHECK(amount > 0), currency varchar(3) NOT NULL, provider varchar(64) NOT NULL, provider_reference varchar(160),
    status varchar(20) NOT NULL CHECK(status IN ('PENDING','AUTHORIZED','CAPTURED','FAILED','REFUNDED')), failure_reason varchar(500),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, captured_at timestamptz, version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_payment_invoice FOREIGN KEY (tenant_id,invoice_id) REFERENCES invoice(tenant_id,id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_membership FOREIGN KEY (tenant_id,membership_id) REFERENCES tenant_membership(tenant_id,id) ON DELETE RESTRICT
);
CREATE INDEX ix_payment_tenant_invoice ON payment(tenant_id,invoice_id,created_at DESC);
CREATE TABLE payment_receipt (
    id uuid PRIMARY KEY DEFAULT uuidv7(), tenant_id uuid NOT NULL, payment_id uuid NOT NULL, receipt_number varchar(80) NOT NULL UNIQUE,
    amount numeric(19,2) NOT NULL CHECK(amount > 0), currency varchar(3) NOT NULL, issued_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_receipt_payment UNIQUE(tenant_id,payment_id),
    CONSTRAINT fk_receipt_payment FOREIGN KEY (tenant_id,payment_id) REFERENCES payment(tenant_id,id) ON DELETE RESTRICT
);
CREATE TABLE white_label_settings (
    tenant_id uuid PRIMARY KEY REFERENCES tenant(id) ON DELETE CASCADE, brand_name varchar(120) NOT NULL, primary_color varchar(20),
    logo_url varchar(500), favicon_url varchar(500), custom_domain varchar(120), enabled boolean NOT NULL DEFAULT true,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, version bigint NOT NULL DEFAULT 0
);

INSERT INTO role_permission(role_id,permission_key)
SELECT rd.id,rp.permission_key FROM role_definition rd JOIN (VALUES
('FINANCE_ADMINISTRATOR','FINANCE_VIEW'),('FINANCE_ADMINISTRATOR','FINANCE_MANAGE'),('FINANCE_ADMINISTRATOR','PAYMENTS_MANAGE'),
('FINANCE_ADMINISTRATOR','COMMERCIAL_VIEW'),('FINANCE_ADMINISTRATOR','COMMERCIAL_MANAGE'),('ORGANIZATION_OWNER','FINANCE_VIEW'),('ORGANIZATION_OWNER','FINANCE_MANAGE'),('ORGANIZATION_OWNER','PAYMENTS_MANAGE'),('ORGANIZATION_OWNER','COMMERCIAL_VIEW'),('ORGANIZATION_OWNER','COMMERCIAL_MANAGE'),('ORGANIZATION_ADMINISTRATOR','FINANCE_VIEW'),('ORGANIZATION_ADMINISTRATOR','FINANCE_MANAGE'),('ORGANIZATION_ADMINISTRATOR','PAYMENTS_MANAGE'),('ORGANIZATION_ADMINISTRATOR','COMMERCIAL_VIEW'),('ORGANIZATION_ADMINISTRATOR','COMMERCIAL_MANAGE'),('BRANCH_ADMINISTRATOR','FINANCE_VIEW')) AS rp(system_key,permission_key) ON rd.system_key=rp.system_key
ON CONFLICT DO NOTHING;
