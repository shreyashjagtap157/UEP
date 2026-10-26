-- User-controlled messaging blocks are tenant-scoped and cannot cross membership boundaries.
create table message_block (
    id uuid primary key,
    tenant_id uuid not null references tenant(id) on delete cascade,
    blocker_membership_id uuid not null,
    blocked_membership_id uuid not null,
    created_at timestamptz not null,
    constraint uq_message_block_tenant_id unique (tenant_id, id),
    constraint uq_message_block_pair unique (tenant_id, blocker_membership_id, blocked_membership_id),
    constraint fk_message_block_blocker foreign key (tenant_id, blocker_membership_id)
        references tenant_membership (tenant_id, id) on delete cascade,
    constraint fk_message_block_blocked foreign key (tenant_id, blocked_membership_id)
        references tenant_membership (tenant_id, id) on delete cascade,
    constraint ck_message_block_not_self check (blocker_membership_id <> blocked_membership_id)
);

create index ix_message_block_blocked on message_block (tenant_id, blocked_membership_id, blocker_membership_id);
