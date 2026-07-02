create sequence if not exists drug_return_no_seq start 1 increment 1;

create table if not exists drug_return_request (
    id               uuid          primary key default gen_random_uuid(),
    return_no        varchar(32)   not null unique,
    prescription_id  uuid          not null references prescription(id),
    patient_id       uuid          not null,
    patient_name     varchar(64)   not null,
    doctor_id        varchar(64)   not null,
    doctor_opinion   varchar(512)  not null,
    opinion_template varchar(128),
    status           varchar(32)   not null default 'PENDING_VERIFY'
                                     check (status in ('PENDING_VERIFY','PHARMACY_CONFIRMED','PHARMACY_COMPLETED','REJECTED')),
    total_amount     numeric(12,2) not null default 0,
    pharmacist_id    varchar(64),
    pharmacist_opinion varchar(512),
    cashier_id       varchar(64),
    refund_order_id  varchar(64),
    created_at       timestamptz   not null default now(),
    verified_at      timestamptz,
    completed_at     timestamptz,
    updated_at       timestamptz   not null default now()
);
create index if not exists idx_drug_return_patient on drug_return_request(patient_id, created_at desc);
create index if not exists idx_drug_return_status on drug_return_request(status, created_at desc);
create unique index if not exists uk_drug_return_active_prescription
    on drug_return_request(prescription_id)
    where status in ('PENDING_VERIFY','PHARMACY_CONFIRMED');

create table if not exists drug_return_item (
    id               uuid          primary key default gen_random_uuid(),
    return_id        uuid          not null references drug_return_request(id) on delete cascade,
    prescription_item_id uuid      not null references prescription_item(id),
    drug_id          uuid          not null references drug(id),
    drug_name        varchar(128)  not null,
    quantity         integer       not null check (quantity > 0),
    unit_price       numeric(10,2) not null,
    amount           numeric(12,2) not null,
    batch_no         varchar(64),
    batch_no_matched boolean,
    cold_chain_or_opened_reject_type boolean,
    package_intact   boolean,
    seal_broken      boolean,
    pharmacist_note  varchar(256)
);
create index if not exists idx_drug_return_item_return on drug_return_item(return_id);
