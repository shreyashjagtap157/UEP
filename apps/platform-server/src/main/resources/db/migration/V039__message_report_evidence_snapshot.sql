-- Preserve the content and authorship that were actually reported, even if the source message is later redacted.
alter table message_report
    add column message_sender_membership_id uuid,
    add column reported_message_body text,
    add column message_created_at timestamptz;

update message_report r
set message_sender_membership_id = m.sender_membership_id,
    reported_message_body = m.body,
    message_created_at = m.created_at
from message_record m
where m.tenant_id = r.tenant_id
  and m.conversation_id = r.conversation_id
  and m.id = r.message_id;

alter table message_report
    alter column message_sender_membership_id set not null,
    alter column reported_message_body set not null,
    alter column message_created_at set not null;

alter table message_report
    add constraint fk_message_report_sender_participant
    foreign key (tenant_id, conversation_id, message_sender_membership_id)
    references message_participant (tenant_id, conversation_id, membership_id)
    on delete restrict;

alter table message_report
    add constraint ck_message_report_evidence_body
    check (length(trim(reported_message_body)) between 1 and 20000);

create index ix_message_report_sender on message_report (tenant_id, message_sender_membership_id, created_at desc);
