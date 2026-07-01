alter table medical_order
    drop constraint if exists medical_order_status_check;

alter table medical_order
    add constraint medical_order_status_check
        check (status in (
            'PENDING_PAYMENT',
            'WAITING_TRIAGE',
            'WAITING',
            'CALLED',
            'IN_PROGRESS',
            'COMPLETED',
            'CANCELLED',
            'MISSED'
        ));
