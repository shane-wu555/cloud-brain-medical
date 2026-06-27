create table if not exists auth_audit_log (
    id uuid primary key,
    event_type varchar(32) not null,
    username varchar(64),
    user_id varchar(64),
    success boolean not null,
    failure_reason varchar(128),
    client_ip varchar(64),
    user_agent varchar(512),
    occurred_at timestamptz not null default now()
);

create index if not exists idx_auth_audit_user_time
    on auth_audit_log (user_id, occurred_at desc);
create index if not exists idx_auth_audit_username_time
    on auth_audit_log (username, occurred_at desc);
