-- Tenant-controlled messaging restrictions, finite retention windows and atomic rate-limit windows.
create table message_tenant_policy (
    tenant_id uuid primary key references tenant(id) on delete cascade,
    messaging_enabled boolean not null default true,
    allow_student_to_student boolean not null default true,
    message_retention_days integer not null default 365,
    report_retention_days integer not null default 1095,
    max_messages_per_minute integer not null default 30,
    max_reports_per_hour integer not null default 5,
    updated_at timestamptz not null default current_timestamp,
    version bigint not null default 0,
    constraint ck_message_policy_message_retention check (message_retention_days between 1 and 3650),
    constraint ck_message_policy_report_retention check (report_retention_days between message_retention_days and 7300),
    constraint ck_message_policy_send_rate check (max_messages_per_minute between 1 and 120),
    constraint ck_message_policy_report_rate check (max_reports_per_hour between 1 and 20)
);

insert into message_tenant_policy (tenant_id)
select id from tenant
on conflict (tenant_id) do nothing;

create table message_rate_window (
    tenant_id uuid not null,
    membership_id uuid not null,
    window_type varchar(8) not null,
    window_start timestamptz not null,
    message_count integer not null default 0,
    report_count integer not null default 0,
    primary key (tenant_id, membership_id, window_type, window_start),
    constraint fk_message_rate_window_membership foreign key (tenant_id, membership_id)
        references tenant_membership (tenant_id, id) on delete cascade,
    constraint ck_message_rate_window_type check (window_type in ('MINUTE','HOUR')),
    constraint ck_message_rate_window_counts check (message_count >= 0 and report_count >= 0)
);

create index ix_message_rate_window_expiry on message_rate_window (window_start);
create index ix_message_report_reporter on message_report (tenant_id, reporter_membership_id, created_at desc);
