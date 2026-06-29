create table if not exists audit_log (
    id          bigserial    primary key,
    user_id     varchar(64),
    username    varchar(64),
    role        varchar(32),
    service     varchar(32),
    resource_type varchar(64),
    resource_id varchar(64),
    action      varchar(32),
    request_ip  varchar(48),
    occurred_at timestamptz  not null default now(),
    details     jsonb
);

create index if not exists idx_audit_user    on audit_log(user_id,      occurred_at desc);
create index if not exists idx_audit_service on audit_log(service,      occurred_at desc);
create index if not exists idx_audit_resource on audit_log(resource_type, resource_id);
