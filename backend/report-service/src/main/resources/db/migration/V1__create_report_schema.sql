create table if not exists report_task (
    id           uuid primary key,
    report_type  varchar(64)  not null,
    title        varchar(256) not null,
    status       varchar(32)  not null default 'PENDING',
    params       text,
    result       text,
    error_message varchar(512),
    created_by   varchar(64)  not null,
    created_at   timestamptz  not null default now(),
    completed_at timestamptz
);

create index if not exists idx_report_task_created_by
    on report_task (created_by, created_at desc);
create index if not exists idx_report_task_status
    on report_task (status, created_at desc);
