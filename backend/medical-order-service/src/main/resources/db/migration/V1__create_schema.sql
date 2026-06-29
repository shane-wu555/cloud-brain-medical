-- ══════════════════════════════════════════════════════════════════
-- 执行诊室（检查室/检验室/处置室）
--   id 格式：rm-chk-01, rm-lab-01, rm-dsp-01 等
-- ══════════════════════════════════════════════════════════════════
create table if not exists examination_room (
    id           varchar(32)  primary key,
    name         varchar(64)  not null,
    room_type    varchar(16)  not null check (room_type in ('CHECK','LAB','DISPOSAL')),
    location     varchar(128) not null,
    equipment_ids text,
    capacity     integer      not null default 20 check (capacity > 0),
    active       boolean      not null default true
);

-- 诊室可处理的项目
create table if not exists room_item_capability (
    room_id   varchar(32)  not null references examination_room(id),
    item_code varchar(32)  not null,
    item_name varchar(128) not null,
    priority  integer      not null default 100,
    active    boolean      not null default true,
    primary key (room_id, item_code)
);

-- 员工-诊室绑定（staff_id = doctor.staff.id = 工号）
create table if not exists staff_room_assignment (
    staff_id    varchar(64) not null,
    room_id     varchar(32) not null references examination_room(id),
    active      boolean     not null default true,
    assigned_at timestamptz not null default now(),
    primary key (staff_id, room_id)
);
create index idx_sra_staff on staff_room_assignment(staff_id, active);
create index idx_sra_room  on staff_room_assignment(room_id,  active);

-- ══════════════════════════════════════════════════════════════════
-- 医技申请单（检查/检验/处置）
-- ══════════════════════════════════════════════════════════════════
create table if not exists medical_order (
    id                   uuid        primary key default gen_random_uuid(),
    appointment_id       uuid        not null,
    patient_id           uuid        not null,
    patient_name         varchar(64) not null,
    ordering_doctor_id   varchar(64) not null,
    room_id              varchar(32) references examination_room(id),
    executing_staff_id   varchar(64),
    order_type           varchar(16) not null check (order_type in ('CHECK','LAB','DISPOSAL')),
    item_code            varchar(32) not null,
    item_name            varchar(128) not null,
    body_part            varchar(128),
    purpose              text,
    amount               numeric(12,2) not null default 0,
    payment_status       varchar(8)  not null default 'UNPAID'
                                     check (payment_status in ('UNPAID','PAID')),
    status               varchar(24) not null default 'PENDING_PAYMENT' check (status in (
                             'PENDING_PAYMENT','WAITING_TRIAGE','WAITING',
                             'IN_PROGRESS','COMPLETED','CANCELLED','MISSED')),
    urgency              varchar(16) not null default 'ROUTINE'
                                     check (urgency in ('ROUTINE','EMERGENCY')),
    queue_number         integer,
    triage_source        varchar(8)  check (triage_source in ('AI','RULE')),
    triage_reasons       text,
    missed_count         integer     not null default 0,
    result_summary       text,
    result_created_by_type varchar(8) check (result_created_by_type in ('HUMAN','AI')),
    result_ai_record_id  varchar(64),
    result_confirmed_by  varchar(64),
    result_confirmed_at  timestamptz,
    created_at           timestamptz not null default now(),
    started_at           timestamptz,
    completed_at         timestamptz,
    check (result_created_by_type = 'HUMAN' or result_ai_record_id is not null)
);
create index idx_order_appointment on medical_order(appointment_id);
create index idx_order_patient     on medical_order(patient_id);
create index idx_order_room_queue  on medical_order(room_id, status, urgency, queue_number);
create index idx_order_type_status on medical_order(order_type, status, created_at);
create unique index uk_order_appt_item
    on medical_order(appointment_id, item_code)
    where status not in ('CANCELLED');

-- 检查/检验报告
create table if not exists medical_report (
    id                    uuid        primary key default gen_random_uuid(),
    order_id              uuid        not null unique references medical_order(id),
    report_type           varchar(16) not null check (report_type in ('CHECK','LAB','DISPOSAL')),
    status                varchar(16) not null default 'DRAFT'
                                      check (status in ('DRAFT','CONFIRMED','REJECTED')),
    findings              text,
    conclusion            text,
    advice                text,
    created_by_type       varchar(8)  not null default 'HUMAN'
                                      check (created_by_type in ('HUMAN','AI')),
    ai_task_id            uuid,
    ai_original_findings  text,
    ai_original_conclusion text,
    modified_from_ai      boolean     not null default false,
    confirmed_by          varchar(64),
    confirmed_at          timestamptz,
    rejected_by           varchar(64),
    rejected_at           timestamptz,
    rejection_reason      varchar(256),
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now()
);

-- 检验标本
create table if not exists specimen (
    id            uuid        primary key default gen_random_uuid(),
    order_id      uuid        not null references medical_order(id),
    specimen_type varchar(64) not null,
    barcode       varchar(128) not null unique,
    status        varchar(16) not null default 'REQUESTED' check (status in (
                      'REQUESTED','COLLECTED','RECEIVED','ANALYZING',
                      'REVIEWED','EXHAUSTED','DISCARDED')),
    collected_at  timestamptz,
    received_at   timestamptz,
    analyzed_at   timestamptz,
    completed_at  timestamptz,
    discarded_at  timestamptz,
    discard_reason varchar(255),
    created_at    timestamptz not null default now()
);
create index idx_specimen_order  on specimen(order_id);
create index idx_specimen_status on specimen(status, created_at);

-- 检验结果项
create table if not exists lab_result_item (
    id               uuid        primary key default gen_random_uuid(),
    order_id         uuid        not null references medical_order(id),
    specimen_id      uuid        not null references specimen(id),
    item_code        varchar(64) not null,
    item_name        varchar(128) not null,
    result_value     varchar(255) not null,
    unit             varchar(32),
    reference_range  varchar(128),
    abnormal_flag    varchar(8)  check (abnormal_flag in ('NORMAL','HIGH','LOW','CRITICAL')),
    created_by_type  varchar(8)  not null default 'HUMAN' check (created_by_type in ('HUMAN','AI')),
    ai_record_id     varchar(64),
    confirmed_by     varchar(64) not null,
    confirmed_at     timestamptz not null default now(),
    created_at       timestamptz not null default now(),
    unique (specimen_id, item_code),
    check (created_by_type = 'HUMAN' or ai_record_id is not null)
);
create index idx_lab_result_order on lab_result_item(order_id, item_code);

-- 附件（CT影像等）
create table if not exists attachment (
    id            uuid        primary key default gen_random_uuid(),
    order_id      uuid        not null references medical_order(id),
    bucket        varchar(128) not null,
    object_key    varchar(512) not null unique,
    original_name varchar(256) not null,
    content_type  varchar(128),
    size_bytes    bigint       not null,
    uploaded_by   varchar(64)  not null,
    created_at    timestamptz  not null default now()
);
create index idx_attachment_order on attachment(order_id);

-- AI 任务记录
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
create index idx_ai_task_order on ai_task(order_id);
