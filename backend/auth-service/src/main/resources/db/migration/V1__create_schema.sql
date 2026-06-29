-- ══════════════════════════════════════════════════════════════════
-- user_account — 全院统一账号
--   员工：id = username = employee_no（8位工号）
--   患者：id = UUID，username = 手机号
-- ══════════════════════════════════════════════════════════════════
create table if not exists user_account (
    id         varchar(64)  primary key,
    username   varchar(64)  not null unique,
    password   varchar(128) not null,
    phone      varchar(16),
    name       varchar(64)  not null,
    role       varchar(32)  not null check (role in (
                   'ADMIN','OUTPATIENT_DOCTOR','CHECK_DOCTOR','LAB_DOCTOR',
                   'DISPOSAL_DOCTOR','PHARMACY_STAFF','CASHIER','PATIENT')),
    permissions text not null default '',
    real_name_verified boolean not null default false,
    employee_no varchar(64),
    active     boolean      not null default true,
    created_at timestamptz  not null default now()
);
create index if not exists idx_user_phone on user_account(phone) where phone is not null;
create index if not exists idx_user_employee_no on user_account(employee_no) where employee_no is not null;

-- 认证审计
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

-- 短信验证码
create table if not exists verification_code (
    id         uuid        primary key,
    phone      varchar(16) not null,
    code_hash  varchar(128) not null,
    purpose    varchar(32) not null check (purpose in ('REGISTER','LOGIN','RESET_PASSWORD')),
    expires_at timestamptz not null,
    consumed_at timestamptz,
    created_at timestamptz not null default now()
);
create index if not exists idx_vcode_phone on verification_code(phone, purpose, created_at desc);

-- 平台管理员（密码：abc12345）
insert into user_account (id, username, password, name, role)
values ('00990001', '00990001',
        '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.',
        '平台管理员', 'ADMIN')
on conflict (id) do nothing;
