alter table if exists account_binding
    add column if not exists medical_insurance_bound boolean not null default false;

alter table if exists account_binding
    add column if not exists medical_insurance_no varchar(64);
