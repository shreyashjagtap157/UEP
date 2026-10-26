-- Ensure message ownership cannot be detached from tenant memberships or participants.

alter table message_conversation
    add constraint fk_message_conversation_creator_membership
    foreign key (tenant_id, created_by_membership_id)
    references tenant_membership (tenant_id, id)
    on delete restrict;

alter table message_participant
    add constraint fk_message_participant_membership
    foreign key (tenant_id, membership_id)
    references tenant_membership (tenant_id, id)
    on delete restrict;

alter table message_participant
    add constraint uq_message_participant_tenant_conversation_membership
    unique (tenant_id, conversation_id, membership_id);

alter table message_record
    add constraint fk_message_record_sender_participant
    foreign key (tenant_id, conversation_id, sender_membership_id)
    references message_participant (tenant_id, conversation_id, membership_id)
    on delete restrict;

create index ix_message_conversation_creator_membership
    on message_conversation (tenant_id, created_by_membership_id);

create index ix_message_record_sender_participant
    on message_record (tenant_id, conversation_id, sender_membership_id);
