-- Persist reports with tenant, conversation, message, reporter and reviewer integrity.
alter table message_record
    add constraint uq_message_record_tenant_conversation_id
    unique (tenant_id, conversation_id, id);

create table message_report (
    id uuid primary key,
    tenant_id uuid not null references tenant(id) on delete cascade,
    conversation_id uuid not null,
    message_id uuid not null,
    reporter_membership_id uuid not null,
    reason varchar(2000) not null,
    status varchar(24) not null default 'OPEN',
    created_at timestamptz not null,
    reviewed_at timestamptz,
    reviewed_by_membership_id uuid,
    resolution varchar(2000),
    version bigint not null default 0,
    constraint uq_message_reporter_message unique (tenant_id, message_id, reporter_membership_id),
    constraint fk_message_report_message foreign key (tenant_id, conversation_id, message_id)
        references message_record (tenant_id, conversation_id, id) on delete cascade,
    constraint fk_message_report_reporter foreign key (tenant_id, conversation_id, reporter_membership_id)
        references message_participant (tenant_id, conversation_id, membership_id) on delete cascade,
    constraint fk_message_report_reviewer foreign key (tenant_id, reviewed_by_membership_id)
        references tenant_membership (tenant_id, id) on delete cascade,
    constraint ck_message_report_reason check (length(trim(reason)) between 1 and 2000),
    constraint ck_message_report_status check (status in ('OPEN','REVIEWED','DISMISSED','ACTIONED')),
    constraint ck_message_report_resolution_state check (
        (status = 'OPEN' and reviewed_at is null and reviewed_by_membership_id is null and resolution is null)
        or
        (status <> 'OPEN' and reviewed_at is not null and reviewed_by_membership_id is not null
            and resolution is not null and length(trim(resolution)) between 1 and 2000)
    )
);

create index ix_message_report_queue on message_report (tenant_id, status, created_at desc, id);
create index ix_message_report_message on message_report (tenant_id, message_id, created_at desc);

insert into role_permission (role_id, permission_key)
select rd.id, 'MESSAGING_MODERATE'
from role_definition rd
where rd.system_key in ('ORGANIZATION_OWNER', 'ORGANIZATION_ADMINISTRATOR', 'SUPPORT_OPERATOR')
on conflict (role_id, permission_key) do nothing;
