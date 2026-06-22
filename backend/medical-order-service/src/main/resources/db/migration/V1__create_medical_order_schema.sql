create table if not exists medical_order (
    id varchar(64) primary key,
    appointment_id varchar(64) not null,
    patient_id varchar(64) not null,
    patient_name varchar(64) not null,
    ordering_doctor_id varchar(64) not null,
    order_type varchar(32) not null check (order_type in ('CHECK', 'LAB', 'DISPOSAL')),
    project_code varchar(64) not null,
    project_name varchar(128) not null,
    purpose text,
    body_part varchar(128),
    amount numeric(12, 2) not null default 0 check (amount >= 0),
    payment_status varchar(32) not null default 'UNPAID',
    status varchar(32) not null default 'PENDING_PAYMENT',
    executor_id varchar(64),
    result_data jsonb,
    result_summary text,
    created_at timestamp not null default now(),
    started_at timestamp,
    completed_at timestamp
);

create index if not exists idx_medical_order_queue on medical_order (order_type, status, created_at);
create index if not exists idx_medical_order_appointment on medical_order (appointment_id);
create index if not exists idx_medical_order_patient on medical_order (patient_id);
