alter table if exists patient_notification
    drop constraint if exists patient_notification_category_check;

alter table if exists patient_notification
    add constraint patient_notification_category_check check (category in (
        'PENDING_PAYMENT', 'PAYMENT_CONFIRMED', 'REPORT_PUBLISHED',
        'EXAM_COMPLETED', 'DISPOSAL_COMPLETED', 'DRUGS_DISPENSED',
        'DRUG_RETURN_REFUNDED', 'CALLED'));
