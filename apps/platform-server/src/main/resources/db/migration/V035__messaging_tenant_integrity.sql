-- Keep tenant identity bound to the owning conversation at the database boundary.
-- The application already enforces this relationship; these constraints make
-- cross-tenant corruption impossible even if a future write path bypasses it.

alter table message_conversation
    add constraint uq_message_conversation_id_tenant unique (id, tenant_id);

alter table message_participant
    add constraint fk_message_participant_conversation_tenant
    foreign key (conversation_id, tenant_id)
    references message_conversation (id, tenant_id)
    on delete cascade;

alter table message_record
    add constraint fk_message_record_conversation_tenant
    foreign key (conversation_id, tenant_id)
    references message_conversation (id, tenant_id)
    on delete cascade;
