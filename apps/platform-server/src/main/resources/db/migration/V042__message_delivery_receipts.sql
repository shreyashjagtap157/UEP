-- Durable tenant- and conversation-scoped per-recipient delivery/read receipts.
create table message_delivery (
    id uuid primary key default uuidv7(),
    tenant_id uuid not null references tenant(id) on delete cascade,
    conversation_id uuid not null,
    message_id uuid not null,
    recipient_membership_id uuid not null,
    status varchar(16) not null default 'PENDING',
    created_at timestamptz not null,
    delivered_at timestamptz,
    read_at timestamptz,
    version bigint not null default 0,
    constraint uq_message_delivery_recipient unique (tenant_id, message_id, recipient_membership_id),
    constraint fk_message_delivery_message foreign key (tenant_id, conversation_id, message_id)
        references message_record (tenant_id, conversation_id, id) on delete cascade,
    constraint fk_message_delivery_recipient foreign key (tenant_id, conversation_id, recipient_membership_id)
        references message_participant (tenant_id, conversation_id, membership_id) on delete cascade,
    constraint ck_message_delivery_state check (
        (status = 'PENDING' and delivered_at is null and read_at is null)
        or (status = 'DELIVERED' and delivered_at is not null and read_at is null)
        or (status = 'READ' and delivered_at is not null and read_at is not null)
    )
);

create index ix_message_delivery_recipient_status
    on message_delivery (tenant_id, recipient_membership_id, status, message_id);
create index ix_message_delivery_message_status
    on message_delivery (tenant_id, message_id, status);
