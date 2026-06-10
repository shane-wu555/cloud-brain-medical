create table if not exists slot_inventory (
    schedule_id varchar(64) primary key,
    capacity integer not null,
    locked integer not null default 0,
    booked integer not null default 0
);

create table if not exists appointment (
    id varchar(64) primary key,
    schedule_id varchar(64) not null,
    patient_id varchar(64) not null,
    patient_name varchar(64) not null,
    doctor_id varchar(64) not null,
    doctor_name varchar(64) not null,
    department_id varchar(64) not null,
    department_name varchar(64) not null,
    visit_date date not null,
    period varchar(32) not null,
    source varchar(32) not null,
    status varchar(32) not null,
    payment_status varchar(32) not null,
    payment_method varchar(32),
    triage_summary text,
    risk_level varchar(32),
    recommended_department_id varchar(64),
    queue_number integer not null,
    missed_count integer not null default 0,
    created_at timestamp not null default now(),
    paid_at timestamp,
    cancelled_at timestamp
);

create index if not exists idx_appointment_doctor_visit on appointment (doctor_id, visit_date, status);
create index if not exists idx_appointment_patient on appointment (patient_id);

insert into slot_inventory (schedule_id, capacity, locked, booked)
values
    ('schedule-001', 20, 0, 8),
    ('schedule-002', 18, 0, 3),
    ('schedule-003', 16, 0, 6),
    ('schedule-004', 30, 0, 12)
on conflict (schedule_id) do nothing;

insert into appointment (
    id, schedule_id, patient_id, patient_name, doctor_id, doctor_name, department_id, department_name,
    visit_date, period, source, status, payment_status, payment_method, triage_summary, risk_level,
    recommended_department_id, queue_number, missed_count, paid_at
)
values (
    'appt-001', 'schedule-001', 'patient-001', '王小云', 'doctor-001', '张医生', 'dept-neuro', '神经内科',
    current_date, '上午', 'ONLINE', 'WAITING', 'PAID', 'WECHAT', 'AI问诊提示：反复头痛，建议神经内科复诊',
    'MEDIUM', 'dept-neuro', 1, 0, now()
)
on conflict (id) do nothing;

