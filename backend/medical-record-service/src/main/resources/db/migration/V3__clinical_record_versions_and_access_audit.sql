alter table medical_record
    add column if not exists past_history text,
    add column if not exists allergy_history text,
    add column if not exists physical_examination text,
    add column if not exists preliminary_diagnosis text,
    add column if not exists version bigint not null default 0;

create table if not exists medical_record_version (
    id uuid primary key,
    medical_record_id varchar(64) not null references medical_record(id),
    version bigint not null,
    chief_complaint text,
    present_illness text,
    past_history text,
    allergy_history text,
    physical_examination text,
    preliminary_diagnosis text,
    treatment_plan text,
    doctor_revision_note text,
    diagnosis_created_by_type varchar(16) not null,
    diagnosis_ai_record_id varchar(64),
    confirmed_by varchar(64) not null,
    created_at timestamptz not null default now(),
    unique (medical_record_id, version)
);

create table if not exists medical_record_access_log (
    id uuid primary key,
    medical_record_id varchar(64) not null references medical_record(id),
    patient_id varchar(64) not null,
    actor_id varchar(64) not null,
    actor_role varchar(32) not null,
    access_scope varchar(32) not null,
    reason varchar(256),
    accessed_at timestamptz not null default now()
);
create index if not exists idx_record_access_patient_time
    on medical_record_access_log(patient_id, accessed_at desc);
