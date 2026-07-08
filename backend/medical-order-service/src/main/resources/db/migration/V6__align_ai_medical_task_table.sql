create table if not exists ai_medical_task (
    id               varchar(36) primary key,
    medical_order_id varchar(36) not null,
    external_task_id varchar(128) not null unique,
    task_type        varchar(32) not null,
    status           varchar(16) not null check (status in ('PENDING','RUNNING','COMPLETED','FAILED')),
    model_version    varchar(128),
    raw_output       jsonb,
    error_message    text,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now()
);

create index if not exists idx_ai_medical_task_order on ai_medical_task(medical_order_id);

do $$
begin
    if to_regclass('medical_order.ai_task') is not null then
        execute $migrate$
            insert into ai_medical_task (
                id,
                medical_order_id,
                external_task_id,
                task_type,
                status,
                model_version,
                raw_output,
                error_message,
                created_at,
                updated_at
            )
            select
                id::text,
                order_id::text,
                external_task_id,
                task_type,
                case status
                    when 'RUNNING' then 'RUNNING'
                    when 'COMPLETED' then 'COMPLETED'
                    when 'FAILED' then 'FAILED'
                    else 'PENDING'
                end,
                model_version,
                coalesce(raw_output, '{}'::jsonb),
                error_message,
                created_at,
                updated_at
            from ai_task
            on conflict (external_task_id) do update set
                status = excluded.status,
                model_version = excluded.model_version,
                raw_output = excluded.raw_output,
                error_message = excluded.error_message,
                updated_at = greatest(ai_medical_task.updated_at, excluded.updated_at)
        $migrate$;
    end if;
end $$;
