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
    paid_at timestamp,
    unique (business_type, business_id)
);
create index if not exists idx_payment_patient on payment_order (patient_id, created_at desc);

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
    refunded_at timestamp,
    unique (business_type, business_id)
);
create index if not exists idx_refund_patient on refund_order (patient_id, created_at desc);
