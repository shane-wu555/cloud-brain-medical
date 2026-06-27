drop index if exists uk_appointment_patient_visit_period_active;

create unique index if not exists uk_appointment_patient_visit_start_time_active
    on appointment (patient_id, visit_date, start_time)
    where status <> 'CANCELLED';
