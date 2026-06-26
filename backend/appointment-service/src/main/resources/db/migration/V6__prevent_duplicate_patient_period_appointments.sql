create unique index if not exists uk_appointment_patient_visit_period_active
    on appointment (patient_id, visit_date, period)
    where status <> 'CANCELLED';
