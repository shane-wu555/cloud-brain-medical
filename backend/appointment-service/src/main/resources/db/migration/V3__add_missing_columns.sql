-- 补充 appointment 表中初次 V1 应用时缺少的列
alter table appointment
    add column if not exists payment_method          varchar(32),
    add column if not exists business_no             varchar(32),
    add column if not exists recommended_department_id varchar(32),
    add column if not exists missed_count            integer not null default 0,
    add column if not exists cancelled_at            timestamptz,
    add column if not exists lock_expires_at         timestamptz;

create unique index if not exists uk_appt_business_no on appointment(business_no)
    where business_no is not null;
create index if not exists idx_appt_lock on appointment(lock_expires_at)
    where status = 'PENDING_PAYMENT';
