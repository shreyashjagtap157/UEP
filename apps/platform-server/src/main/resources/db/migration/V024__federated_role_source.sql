alter table role_assignment add column if not exists source varchar(20) not null default 'MANUAL';
create index if not exists ix_role_assignment_federated on role_assignment(tenant_id, membership_id, source);
