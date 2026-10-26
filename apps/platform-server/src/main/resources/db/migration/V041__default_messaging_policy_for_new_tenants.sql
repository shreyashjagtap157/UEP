-- Backfill tenants created between V040 and V041, then ensure all future tenants receive defaults.
insert into message_tenant_policy (tenant_id)
select id from tenant
on conflict (tenant_id) do nothing;

create or replace function ensure_default_message_tenant_policy()
returns trigger
language plpgsql
as $$
begin
    insert into message_tenant_policy (tenant_id)
    values (new.id)
    on conflict (tenant_id) do nothing;
    return new;
end;
$$;

create trigger trg_tenant_default_message_policy
after insert on tenant
for each row execute function ensure_default_message_tenant_policy();
