create table if not exists report_task (
    id           uuid         primary key default gen_random_uuid(),
    report_type  varchar(64)  not null,
    title        varchar(128) not null,
    status       varchar(16)  not null default 'PENDING'
                              check (status in ('PENDING','RUNNING','COMPLETED','FAILED')),
    params       jsonb,
    result       jsonb,
    error_message text,
    created_by   varchar(64)  not null,
    created_at   timestamptz  not null default now(),
    completed_at timestamptz
);

create index idx_report_creator on report_task(created_by, created_at desc);
create index idx_report_status  on report_task(status,      created_at desc);
