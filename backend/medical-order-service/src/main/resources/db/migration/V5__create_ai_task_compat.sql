create table if not exists ai_task (
    id               uuid        primary key default gen_random_uuid(),
    order_id         uuid        not null references medical_order(id),
    external_task_id varchar(128) not null unique,
    task_type        varchar(32) not null,
    status           varchar(16) not null check (status in ('PENDING','RUNNING','COMPLETED','FAILED')),
    model_version    varchar(128),
    raw_output       jsonb,
    error_message    text,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now()
);

create index if not exists idx_ai_task_order on ai_task(order_id);

do $$
begin
    if to_regclass('medical_order.ai_medical_task') is not null then
        execute $migrate$
            insert into ai_task (
                id,
                order_id,
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
                id::uuid,
                medical_order_id::uuid,
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
            from ai_medical_task
            on conflict (external_task_id) do nothing
        $migrate$;
    end if;
end $$;
