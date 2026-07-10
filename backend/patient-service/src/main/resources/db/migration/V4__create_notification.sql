create table if not exists patient_notification (
    id              uuid         primary key default gen_random_uuid(),
    patient_id      uuid         not null,
    category        varchar(32)  not null check (category in (
                        'PENDING_PAYMENT', 'PAYMENT_CONFIRMED', 'REPORT_PUBLISHED',
                        'EXAM_COMPLETED', 'DISPOSAL_COMPLETED', 'DRUGS_DISPENSED',
                        'DRUG_RETURN_REFUNDED')),
    title           varchar(128) not null,
    body            text,
    reference_type  varchar(32)  not null,
    reference_id    varchar(64)  not null,
    is_read         boolean      not null default false,
    read_at         timestamptz,
    created_at      timestamptz  not null default now()
);

create index if not exists idx_notif_patient_unread
    on patient_notification(patient_id, is_read, created_at desc);
create index if not exists idx_notif_patient_category
    on patient_notification(patient_id, category, created_at desc);
