-- Backfill receipts for messages that predate V042 so unread counts use one consistent state source.
insert into message_delivery (
    id, tenant_id, conversation_id, message_id, recipient_membership_id,
    status, created_at, delivered_at, read_at, version
)
select
    uuidv7(),
    m.tenant_id,
    m.conversation_id,
    m.id,
    p.membership_id,
    case
        when p.last_read_at is not null and p.last_read_at >= m.created_at then 'READ'
        else 'PENDING'
    end,
    m.created_at,
    case
        when p.last_read_at is not null and p.last_read_at >= m.created_at then m.created_at
        else null
    end,
    case
        when p.last_read_at is not null and p.last_read_at >= m.created_at then p.last_read_at
        else null
    end,
    0
from message_record m
join message_participant p
  on p.tenant_id = m.tenant_id
 and p.conversation_id = m.conversation_id
where p.membership_id <> m.sender_membership_id
on conflict (tenant_id, message_id, recipient_membership_id) do nothing;

create index if not exists ix_message_delivery_unread
    on message_delivery (tenant_id, conversation_id, recipient_membership_id, status, message_id);
