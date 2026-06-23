create table if not exists audit_log (
    id          uuid primary key,
    service     varchar(64)  not null,
    event_type  varchar(64)  not null,
    user_id     varchar(64),
    username    varchar(64),
    role        varchar(32),
    resource_type varchar(64),
    resource_id   varchar(64),
    action      varchar(32)  not null,
    success     boolean      not null,
    failure_reason varchar(256),
    client_ip   varchar(64),
    occurred_at timestamptz  not null default now()
);

create index if not exists idx_audit_log_user_time
    on audit_log (user_id, occurred_at desc);
create index if not exists idx_audit_log_service_time
    on audit_log (service, occurred_at desc);
create index if not exists idx_audit_log_resource
    on audit_log (resource_type, resource_id);
