create table if not exists drug_catalog (
    id varchar(64) primary key,
    drug_code varchar(64) not null unique,
    drug_name varchar(128) not null,
    specification varchar(128) not null,
    unit varchar(32) not null,
    unit_price numeric(12, 2) not null check (unit_price >= 0),
    enabled boolean not null default true,
    created_at timestamp not null default now()
);

create table if not exists drug_inventory (
    drug_id varchar(64) primary key references drug_catalog(id),
    quantity integer not null check (quantity >= 0),
    warning_threshold integer not null default 20 check (warning_threshold >= 0),
    updated_at timestamp not null default now()
);

create table if not exists prescription (
    id varchar(64) primary key,
    prescription_no varchar(64) not null unique,
    appointment_id varchar(64) not null,
    medical_record_id varchar(64),
    patient_id varchar(64) not null,
    patient_name varchar(128),
    doctor_id varchar(64) not null,
    diagnosis varchar(512) not null,
    status varchar(32) not null,
    total_amount numeric(12, 2) not null default 0 check (total_amount >= 0),
    payment_order_id varchar(64),
    ai_assistance_id varchar(64),
    ai_adoption_status varchar(32),
    ai_revision_note text,
    created_at timestamp not null default now(),
    confirmed_at timestamp,
    paid_at timestamp,
    dispensed_at timestamp,
    returned_at timestamp,
    dispensed_by varchar(64),
    returned_by varchar(64),
    return_reason varchar(255)
);
create index if not exists idx_prescription_patient on prescription(patient_id, created_at desc);
create index if not exists idx_prescription_status on prescription(status, created_at);

create table if not exists prescription_item (
    id varchar(64) primary key,
    prescription_id varchar(64) not null references prescription(id) on delete cascade,
    drug_id varchar(64) not null references drug_catalog(id),
    drug_name varchar(128) not null,
    quantity integer not null check (quantity > 0),
    dosage varchar(128) not null,
    usage varchar(128) not null,
    frequency varchar(128) not null,
    days integer not null check (days > 0),
    note varchar(255),
    unit_price numeric(12, 2) not null check (unit_price >= 0),
    amount numeric(12, 2) not null check (amount >= 0)
);

create table if not exists inventory_flow (
    id varchar(64) primary key,
    drug_id varchar(64) not null references drug_catalog(id),
    prescription_id varchar(64) not null references prescription(id),
    direction varchar(16) not null,
    quantity integer not null check (quantity > 0),
    before_quantity integer not null check (before_quantity >= 0),
    after_quantity integer not null check (after_quantity >= 0),
    operator_id varchar(64) not null,
    reason varchar(255) not null,
    created_at timestamp not null default now()
);
create index if not exists idx_inventory_flow_prescription on inventory_flow(prescription_id, created_at);

insert into drug_catalog (id, drug_code, drug_name, specification, unit, unit_price)
values
    ('drug-mannitol', 'DRUG-001', '甘露醇注射液', '250ml:50g', '瓶', 12.80),
    ('drug-aspirin', 'DRUG-002', '阿司匹林肠溶片', '100mg*30片', '盒', 18.50),
    ('drug-atorvastatin', 'DRUG-003', '阿托伐他汀钙片', '20mg*7片', '盒', 29.00)
on conflict (drug_code) do nothing;

insert into drug_inventory (drug_id, quantity, warning_threshold)
values
    ('drug-mannitol', 120, 20),
    ('drug-aspirin', 200, 30),
    ('drug-atorvastatin', 160, 30)
on conflict (drug_id) do nothing;
