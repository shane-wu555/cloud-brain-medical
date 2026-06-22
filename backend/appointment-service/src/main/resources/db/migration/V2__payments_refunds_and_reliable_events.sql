alter table slot_inventory
    add constraint chk_slot_inventory_non_negative check (capacity >= 0 and locked >= 0 and booked >= 0),
    add constraint chk_slot_inventory_capacity check (locked + booked <= capacity);

create unique index if not exists uk_appointment_doctor_queue
    on appointment (doctor_id, visit_date, queue_number)
    where status <> 'CANCELLED';

create table if not exists payment_order (
    id varchar(64) primary key,
    business_type varchar(32) not null,
    business_id varchar(64) not null,
    patient_id varchar(64) not null,
    amount numeric(12, 2) not null check (amount >= 0),
    payment_method varchar(32) not null,
    status varchar(32) not null,
    operator_id varchar(64) not null,
    created_at timestamp not null default now(),
    paid_at timestamp
);

create index if not exists idx_payment_business on payment_order (business_type, business_id);
create unique index if not exists uk_paid_business_order
    on payment_order (business_type, business_id)
    where status = 'PAID';

create table if not exists refund_order (
    id varchar(64) primary key,
    business_type varchar(32) not null,
    business_id varchar(64) not null,
    patient_id varchar(64) not null,
    amount numeric(12, 2) not null check (amount >= 0),
    reason varchar(255) not null,
    status varchar(32) not null,
    operator_id varchar(64) not null,
    created_at timestamp not null default now(),
    refunded_at timestamp
);

create index if not exists idx_refund_business on refund_order (business_type, business_id);

create table if not exists integration_event (
    id varchar(64) primary key,
    aggregate_id varchar(64) not null,
    event_type varchar(64) not null,
    payload jsonb not null,
    status varchar(32) not null,
    retry_count integer not null default 0,
    next_attempt_at timestamp not null default now(),
    last_error text,
    created_at timestamp not null default now(),
    completed_at timestamp,
    unique (aggregate_id, event_type)
);

create index if not exists idx_integration_event_dispatch
    on integration_event (status, next_attempt_at, created_at);
