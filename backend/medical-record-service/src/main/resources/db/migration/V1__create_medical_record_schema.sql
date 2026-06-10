create table if not exists medical_record (
    id varchar(64) primary key,
    appointment_id varchar(64) not null unique,
    patient_id varchar(64) not null,
    patient_name varchar(64) not null,
    doctor_id varchar(64) not null,
    doctor_name varchar(64) not null,
    department_name varchar(64) not null,
    visit_date varchar(32) not null,
    period varchar(32) not null,
    ai_triage_summary text,
    ai_risk_level varchar(32),
    chief_complaint text,
    present_illness text,
    diagnosis text,
    treatment_plan text,
    doctor_revision_note text,
    status varchar(32) not null,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now(),
    archived_at timestamp
);

create index if not exists idx_medical_record_patient on medical_record (patient_id);
create index if not exists idx_medical_record_status on medical_record (status);

