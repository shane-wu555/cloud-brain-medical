create table if not exists medical_record (
    id                        uuid        primary key default gen_random_uuid(),
    appointment_id            uuid        not null unique,
    patient_id                uuid        not null,
    patient_name              varchar(64) not null,
    doctor_id                 varchar(64) not null,
    doctor_name               varchar(64) not null,
    department_name           varchar(64) not null,
    visit_date                date        not null,
    ai_triage_summary         text,
    chief_complaint           text,
    present_illness           text,
    past_history              text,
    allergy_history           text,
    physical_examination      text,
    preliminary_diagnosis     text,
    diagnosis                 text,
    treatment_plan            text,
    status                    varchar(16) not null default 'DRAFT'
                                          check (status in ('DRAFT','COMPLETED','ARCHIVED')),
    version                   integer     not null default 1,
    diagnosis_created_by_type varchar(8)  check (diagnosis_created_by_type in ('HUMAN','AI')),
    diagnosis_ai_record_id    varchar(64),
    diagnosis_confirmed_by    varchar(64),
    diagnosis_confirmed_at    timestamptz,
    created_at                timestamptz not null default now(),
    updated_at                timestamptz not null default now()
);
create index idx_record_patient on medical_record(patient_id,  created_at desc);
create index idx_record_doctor  on medical_record(doctor_id,   visit_date);

create table if not exists record_version (
    id        uuid        primary key default gen_random_uuid(),
    record_id uuid        not null references medical_record(id),
    version   integer     not null,
    content   jsonb       not null,
    author_id varchar(64) not null,
    created_at timestamptz not null default now(),
    unique (record_id, version)
);
create index idx_record_version on record_version(record_id, version desc);

create table if not exists access_log (
    id        bigserial   primary key,
    record_id uuid        not null,
    patient_id uuid       not null,
    actor_id  varchar(64) not null,
    actor_role varchar(32) not null,
    reason    text,
    scope     varchar(16) not null default 'FULL' check (scope in ('FULL','LIMITED')),
    accessed_at timestamptz not null default now()
);
create index idx_access_record  on access_log(record_id,  accessed_at desc);
create index idx_access_patient on access_log(patient_id, accessed_at desc);
