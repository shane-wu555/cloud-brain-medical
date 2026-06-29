alter table user_account
    add column if not exists phone varchar(16),
    add column if not exists permissions text not null default '',
    add column if not exists real_name_verified boolean not null default false,
    add column if not exists employee_no varchar(64);

update user_account
set employee_no = username
where role <> 'PATIENT'
  and employee_no is null;

update user_account
set phone = username
where role = 'PATIENT'
  and phone is null;

create index if not exists idx_user_phone on user_account(phone) where phone is not null;
create index if not exists idx_user_employee_no on user_account(employee_no) where employee_no is not null;

create table if not exists auth_audit_log (
    id             uuid        primary key,
    event_type     varchar(32) not null,
    username       varchar(64),
    user_id        varchar(64),
    success        boolean     not null default true,
    failure_reason varchar(128),
    client_ip      varchar(64),
    user_agent     varchar(512),
    occurred_at    timestamptz not null default now()
);
create index if not exists idx_auth_audit_user on auth_audit_log(user_id, occurred_at desc);

drop table if exists auth_audit;

alter table verification_code
    add column if not exists code_hash varchar(128),
    add column if not exists code varchar(128),
    add column if not exists consumed_at timestamptz;

update verification_code
set code_hash = coalesce(code_hash, code, '')
where code_hash is null;

alter table verification_code
    alter column id drop default;

alter table verification_code
    alter column id type uuid using gen_random_uuid();

alter table verification_code
    alter column id set default gen_random_uuid();

alter table verification_code
    alter column code_hash set not null;

alter table verification_code
    drop column if exists code,
    drop column if exists used_at;
