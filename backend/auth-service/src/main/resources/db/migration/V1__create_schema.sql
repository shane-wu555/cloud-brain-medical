-- ══════════════════════════════════════════════════════════════════
-- user_account — 全院统一账号
--   员工：id = username = employee_no（8位工号）
--   患者：id = UUID，username = 手机号
-- ══════════════════════════════════════════════════════════════════
create table if not exists user_account (
    id         varchar(64)  primary key,
    username   varchar(64)  not null unique,
    password   varchar(128) not null,
    name       varchar(64)  not null,
    role       varchar(32)  not null check (role in (
                   'ADMIN','OUTPATIENT_DOCTOR','CHECK_DOCTOR','LAB_DOCTOR',
                   'DISPOSAL_DOCTOR','PHARMACY_STAFF','CASHIER','PATIENT')),
    active     boolean      not null default true,
    created_at timestamptz  not null default now()
);

-- 认证审计
create table if not exists auth_audit (
    id         bigserial   primary key,
    user_id    varchar(64),
    username   varchar(64),
    event_type varchar(32) not null,
    ip         varchar(48),
    occurred_at timestamptz not null default now()
);
create index if not exists idx_auth_audit_user on auth_audit(user_id, occurred_at desc);

-- 短信验证码
create table if not exists verification_code (
    id         bigserial   primary key,
    phone      varchar(16) not null,
    code       varchar(8)  not null,
    purpose    varchar(32) not null check (purpose in ('REGISTER','LOGIN','RESET_PASSWORD')),
    expires_at timestamptz not null,
    used_at    timestamptz,
    created_at timestamptz not null default now()
);
create index if not exists idx_vcode_phone on verification_code(phone, purpose, created_at desc);

-- 平台管理员（密码：abc12345）
insert into user_account (id, username, password, name, role)
values ('00990001', '00990001',
        '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.',
        '平台管理员', 'ADMIN')
on conflict (id) do nothing;
