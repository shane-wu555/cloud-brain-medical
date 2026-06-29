-- ══════════════════════════════════════════════════════════════════
-- 新设计：
--   slot_inventory.slot_id  = doctor.schedule_slot.id
--   appointment.slot_id     = doctor.schedule_slot.id
--   appointment.id          = varchar（Java 代码以字符串形式传入 UUID）
--   outbox_event            = 替代旧 integration_event
-- ══════════════════════════════════════════════════════════════════

-- 号源库存（key = schedule_slot.id）
create table if not exists slot_inventory (
    slot_id  varchar(64) primary key,
    capacity integer     not null check (capacity > 0),
    locked   integer     not null default 0 check (locked >= 0),
    booked   integer     not null default 0 check (booked >= 0),
    check (locked + booked <= capacity)
);

-- 业务编号序列
create sequence if not exists appt_business_no_seq start 1 increment 1;

-- 挂号记录
create table if not exists appointment (
    id                        varchar(64)  primary key,
    slot_id                   varchar(64)  not null,
    patient_id                varchar(64)  not null,
    patient_name              varchar(64)  not null,
    doctor_id                 varchar(64)  not null,
    doctor_name               varchar(64)  not null,
    department_id             varchar(32)  not null,
    department_name           varchar(64)  not null,
    visit_date                date         not null,
    period                    varchar(8)   not null check (period in ('上午','下午','全天')),
    start_time                time         not null,
    source                    varchar(8)   not null check (source in ('ONLINE','OFFLINE')),
    status                    varchar(24)  not null default 'PENDING_PAYMENT',
    payment_status            varchar(16)  not null default 'UNPAID',
    payment_method            varchar(32),
    queue_number              integer,
    business_no               varchar(32)  unique,
    triage_summary            text,
    risk_level                varchar(8),
    recommended_department_id varchar(32),
    missed_count              integer      not null default 0,
    paid_at                   timestamptz,
    cancelled_at              timestamptz,
    lock_expires_at           timestamptz,
    created_at                timestamptz  not null default now()
);
create index  idx_appt_doctor_date  on appointment(doctor_id, visit_date, status);
create index  idx_appt_patient      on appointment(patient_id);
create index  idx_appt_lock_expires on appointment(lock_expires_at) where status = 'PENDING_PAYMENT';
create unique index uk_appt_patient_slot on appointment(patient_id, slot_id) where status <> 'CANCELLED';
create unique index uk_appt_doctor_date_queue on appointment(doctor_id, visit_date, queue_number)
    where status <> 'CANCELLED' and queue_number is not null;

-- 可靠事件投递（新名称 outbox_event）
create table if not exists outbox_event (
    id              varchar(64)  primary key,
    aggregate_id    varchar(64)  not null,
    event_type      varchar(64)  not null,
    payload         jsonb        not null,
    status          varchar(16)  not null default 'PENDING'
                                 check (status in ('PENDING','PROCESSING','COMPLETED','RETRY','FAILED')),
    retry_count     integer      not null default 0,
    next_attempt_at timestamptz,
    last_error      text,
    created_at      timestamptz  not null default now(),
    completed_at    timestamptz,
    unique (aggregate_id, event_type)
);
create index idx_outbox_pending on outbox_event(status, next_attempt_at)
    where status in ('PENDING','RETRY','PROCESSING');
