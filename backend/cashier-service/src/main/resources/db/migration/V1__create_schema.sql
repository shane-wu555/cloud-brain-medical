create table if not exists payment (
    id               uuid         primary key default gen_random_uuid(),
    business_type    varchar(24)  not null
                                  check (business_type in ('APPOINTMENT','MEDICAL_ORDER','PRESCRIPTION')),
    business_id      varchar(64)  not null,
    patient_id       uuid         not null,
    amount           numeric(12,2) not null check (amount > 0),
    method           varchar(32)  not null,
    channel_trade_no varchar(128),
    status           varchar(16)  not null default 'PENDING'
                                  check (status in ('PENDING','PAID','FAILED','REFUNDED')),
    operator_id      varchar(64),
    failure_reason   varchar(256),
    paid_at          timestamptz,
    created_at       timestamptz  not null default now(),
    unique (business_type, business_id)
);
create unique index uk_payment_trade_no on payment(channel_trade_no) where channel_trade_no is not null;
create index idx_payment_patient on payment(patient_id,  created_at desc);
create index idx_payment_status  on payment(status,      created_at);

create table if not exists refund (
    id               uuid         primary key default gen_random_uuid(),
    payment_id       uuid         not null references payment(id),
    amount           numeric(12,2) not null check (amount > 0),
    reason           varchar(256) not null,
    channel_refund_no varchar(128),
    status           varchar(16)  not null default 'PENDING'
                                  check (status in ('PENDING','REFUNDED','FAILED')),
    operator_id      varchar(64),
    refunded_at      timestamptz,
    created_at       timestamptz  not null default now()
);
create index idx_refund_payment on refund(payment_id);
