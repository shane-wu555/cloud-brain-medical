-- ══════════════════════════════════════════════════════════════════════
-- medical_record 保留业务需要的全部字段；
-- appointment_id / patient_id 改为 uuid 以对齐新 patient/appointment 服务。
-- ══════════════════════════════════════════════════════════════════════
create table if not exists medical_record (
    id                        varchar(64)  primary key,   -- "record-UUID" 格式
    appointment_id            uuid         not null unique,
    patient_id                uuid         not null,
    patient_name              varchar(64)  not null,
    doctor_id                 varchar(64)  not null,
    doctor_name               varchar(64)  not null,
    department_name           varchar(64)  not null,
    visit_date                date         not null,
    period                    varchar(8),
    ai_triage_summary         text,
    ai_risk_level             varchar(8),
    chief_complaint           text,
    present_illness           text,
    past_history              text,
    allergy_history           text,
    physical_examination      text,
    preliminary_diagnosis     text,
    diagnosis                 text,
    treatment_plan            text,
    doctor_revision_note      text,
    status                    varchar(16)  not null default 'DRAFT'
                                           check (status in ('DRAFT','ACTIVE','ARCHIVED')),
    version                   bigint       not null default 0,
    diagnosis_created_by_type varchar(8)   check (diagnosis_created_by_type in ('HUMAN','AI')),
    diagnosis_ai_record_id    varchar(64),
    diagnosis_confirmed_by    varchar(64),
    diagnosis_confirmed_at    timestamptz,
    archived_at               timestamptz,
    created_at                timestamptz  not null default now(),
    updated_at                timestamptz  not null default now()
);
create index if not exists idx_record_patient on medical_record(patient_id,  visit_date desc);
create index if not exists idx_record_doctor  on medical_record(doctor_id,   visit_date desc);

-- 病历修改历史（保留原有列结构供 repository 写入）
create table if not exists medical_record_version (
    id                        uuid         primary key default gen_random_uuid(),
    medical_record_id         varchar(64)  not null references medical_record(id),
    version                   bigint       not null,
    chief_complaint           text,
    present_illness           text,
    past_history              text,
    allergy_history           text,
    physical_examination      text,
    preliminary_diagnosis     text,
    treatment_plan            text,
    doctor_revision_note      text,
    diagnosis_created_by_type varchar(8),
    diagnosis_ai_record_id    varchar(64),
    confirmed_by              varchar(64),
    created_at                timestamptz  not null default now(),
    unique (medical_record_id, version)
);

-- 病历访问审计
create table if not exists medical_record_access_log (
    id              bigserial    primary key,
    medical_record_id varchar(64) not null,
    patient_id      uuid         not null,
    actor_id        varchar(64)  not null,
    actor_role      varchar(32)  not null,
    access_scope    varchar(32)  not null default 'FULL',
    reason          text,
    accessed_at     timestamptz  not null default now()
);
create index if not exists idx_access_record  on medical_record_access_log(medical_record_id, accessed_at desc);
create index if not exists idx_access_patient on medical_record_access_log(patient_id,        accessed_at desc);

-- 病历与报告关联
create table if not exists medical_record_report_link (
    id               uuid         primary key default gen_random_uuid(),
    medical_record_id varchar(64) not null references medical_record(id),
    medical_order_id  uuid        not null,
    report_id         uuid        not null,
    report_type       varchar(32) not null,
    conclusion        text,
    confirmed_by      varchar(64),
    confirmed_at      timestamptz,
    unique (medical_order_id, report_id)
);
