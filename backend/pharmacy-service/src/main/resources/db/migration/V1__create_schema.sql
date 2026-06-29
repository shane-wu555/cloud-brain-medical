create table if not exists drug (
    id            uuid         primary key default gen_random_uuid(),
    code          varchar(32)  not null unique,
    drug_name     varchar(128) not null,
    specification varchar(128) not null,
    unit          varchar(16)  not null,
    unit_price    numeric(10,2) not null check (unit_price >= 0),
    active        boolean      not null default true
);

create table if not exists drug_stock (
    id                uuid    primary key default gen_random_uuid(),
    drug_id           uuid    not null unique references drug(id),
    quantity          integer not null default 0   check (quantity >= 0),
    warning_threshold integer not null default 20  check (warning_threshold >= 0)
);

create sequence if not exists prescription_no_seq start 1 increment 1;

create table if not exists prescription (
    id               uuid        primary key default gen_random_uuid(),
    prescription_no  varchar(32) not null unique,
    appointment_id   uuid        not null,
    medical_record_id uuid,
    patient_id       uuid        not null,
    patient_name     varchar(64) not null,
    doctor_id        varchar(64) not null,
    doctor_name      varchar(64) not null,
    diagnosis        varchar(256),
    status           varchar(24) not null default 'DRAFT' check (status in (
                         'DRAFT','CONFIRMED','PENDING_PAYMENT','PAID',
                         'WAITING_DISPENSE','DISPENSED','RETURNED','CANCELLED')),
    total_amount     numeric(12,2) not null default 0,
    ai_record_id     varchar(64),
    ai_adoption_status varchar(24) check (ai_adoption_status in
                         ('FULL','PARTIAL','REJECTED','HUMAN_ONLY')),
    dispensed_by     varchar(64),
    dispensed_at     timestamptz,
    returned_by      varchar(64),
    returned_at      timestamptz,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now()
);
create index if not exists idx_prescription_patient on prescription(patient_id, created_at desc);
create index if not exists idx_prescription_status  on prescription(status,     created_at);

create table if not exists prescription_item (
    id              uuid        primary key default gen_random_uuid(),
    prescription_id uuid        not null references prescription(id) on delete cascade,
    drug_id         uuid        not null references drug(id),
    drug_name       varchar(128) not null,
    quantity        integer     not null check (quantity > 0),
    dosage          varchar(64),
    usage           varchar(64),
    frequency       varchar(64),
    days            integer     check (days > 0),
    unit_price      numeric(10,2) not null,
    amount          numeric(12,2) not null
);
create index if not exists idx_rx_item_prescription on prescription_item(prescription_id);

create table if not exists stock_flow (
    id           uuid        primary key default gen_random_uuid(),
    drug_id      uuid        not null references drug(id),
    prescription_id uuid     references prescription(id),
    direction    varchar(4)  not null check (direction in ('IN','OUT')),
    quantity     integer     not null check (quantity > 0),
    stock_before integer     not null,
    stock_after  integer     not null,
    operator_id  varchar(64) not null,
    reason       varchar(128),
    created_at   timestamptz not null default now()
);
create index if not exists idx_stock_flow_drug on stock_flow(drug_id, created_at desc);
