-- 号源库存（引用 doctor.schedule_slot.id，跨服务仅靠 id 约定）
create table if not exists slot_inventory (
    slot_id varchar(64)  primary key,
    locked  integer      not null default 0 check (locked >= 0),
    booked  integer      not null default 0 check (booked >= 0)
);

-- ══════════════════════════════════════════════════════════════════
-- 挂号记录
--   doctor_id / patient_id 跨服务引用，不设外键
--   business_no 格式：REG + YYYYMMDD + 6位序号
-- ══════════════════════════════════════════════════════════════════
create sequence if not exists appt_business_no_seq start 1 increment 1;

create table if not exists appointment (
    id              uuid        primary key default gen_random_uuid(),
    slot_id         varchar(64) not null,
    patient_id      uuid        not null,
    patient_name    varchar(64) not null,
    doctor_id       varchar(64) not null,
    doctor_name     varchar(64) not null,
    department_id   varchar(32) not null,
    department_name varchar(64) not null,
    visit_date      date        not null,
    period          varchar(8)  not null check (period in ('上午','下午','全天')),
    start_time      time        not null,
    source          varchar(8)  not null default 'ONLINE' check (source in ('ONLINE','OFFLINE')),
    status          varchar(24) not null default 'PENDING_PAYMENT' check (status in (
                        'PENDING_PAYMENT','WAITING','CALLED','IN_VISIT',
                        'REVISIT_WAITING','FINISHED','CANCELLED')),
    payment_status  varchar(8)  not null default 'UNPAID' check (payment_status in ('UNPAID','PAID','REFUNDED')),
    payment_method  varchar(32),
    queue_number    integer,
    business_no     varchar(32) unique,
    triage_summary  text,
    risk_level      varchar(8)  check (risk_level in ('LOW','MEDIUM','HIGH')),
    missed_count    integer     not null default 0,
    paid_at         timestamptz,
    created_at      timestamptz not null default now()
);
create index  idx_appt_doctor_date on appointment(doctor_id, visit_date, status);
create index  idx_appt_patient     on appointment(patient_id);
create unique index uk_appt_patient_slot on appointment(patient_id, slot_id)
    where status <> 'CANCELLED';
create unique index uk_appt_doctor_date_queue on appointment(doctor_id, visit_date, queue_number)
    where status <> 'CANCELLED' and queue_number is not null;

-- 可靠事件投递（跨服务通知）
create table if not exists outbox_event (
    id             bigserial   primary key,
    aggregate_id   varchar(64) not null,
    event_type     varchar(64) not null,
    payload        jsonb       not null,
    status         varchar(16) not null default 'PENDING'
                               check (status in ('PENDING','PUBLISHED','FAILED')),
    retry_count    integer     not null default 0,
    next_attempt_at timestamptz,
    published_at   timestamptz,
    created_at     timestamptz not null default now()
);
create index idx_outbox_pending on outbox_event(status, next_attempt_at)
    where status in ('PENDING','FAILED');
