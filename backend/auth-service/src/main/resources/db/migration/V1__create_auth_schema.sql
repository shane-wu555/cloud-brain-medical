create table if not exists user_account (
    id               varchar(64)  primary key,
    username         varchar(64)  not null unique,
    password         varchar(128) not null,
    phone            varchar(32)  not null,
    name             varchar(64)  not null,
    role             varchar(32)  not null,
    permissions      text         not null,
    real_name_verified boolean    not null default false,
    employee_no      varchar(8),
    created_at       timestamp    not null default now()
);

-- 工号唯一索引：仅对非 NULL 值生效（患者和管理员 employee_no 为 NULL）
create unique index if not exists uk_user_account_employee_no
    on user_account(employee_no)
    where employee_no is not null;

-- 管理员：统一账号，无工号
-- BCrypt hash 对应密码: abc12345
insert into user_account (id, username, password, phone, name, role, permissions, real_name_verified, employee_no)
values (
    'admin-001', 'admin',
    '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.',
    '13700000000', '平台管理员', 'ADMIN',
    'department:manage,doctor:manage,schedule:manage,dashboard:read',
    true, null
)
on conflict (username) do nothing;
