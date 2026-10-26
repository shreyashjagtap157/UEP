create table message_conversation (
    id uuid primary key,
    tenant_id uuid not null,
    subject varchar(240),
    created_by_membership_id uuid not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0
);

create index ix_message_conversation_tenant_updated on message_conversation (tenant_id, updated_at desc, id);

create table message_participant (
    conversation_id uuid not null references message_conversation(id) on delete cascade,
    tenant_id uuid not null,
    membership_id uuid not null,
    joined_at timestamptz not null,
    last_read_at timestamptz,
    primary key (conversation_id, membership_id)
);

create index ix_message_participant_membership on message_participant (tenant_id, membership_id, conversation_id);

create table message_record (
    id uuid primary key,
    conversation_id uuid not null references message_conversation(id) on delete cascade,
    tenant_id uuid not null,
    sender_membership_id uuid not null,
    body text not null,
    created_at timestamptz not null,
    edited_at timestamptz,
    deleted_at timestamptz,
    version bigint not null default 0,
    constraint ck_message_body_not_blank check (length(trim(body)) between 1 and 20000)
);

create index ix_message_record_conversation_created on message_record (conversation_id, created_at desc, id);
create index ix_message_record_tenant_sender on message_record (tenant_id, sender_membership_id, created_at desc);
