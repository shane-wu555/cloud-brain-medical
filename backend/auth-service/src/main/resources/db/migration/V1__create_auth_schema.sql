create table if not exists user_account (
    id varchar(64) primary key,
    username varchar(64) not null unique,
    password varchar(128) not null,
    phone varchar(32) not null,
    name varchar(64) not null,
    role varchar(32) not null,
    permissions text not null,
    real_name_verified boolean not null default false,
    created_at timestamp not null default now()
);

insert into user_account (id, username, password, phone, name, role, permissions, real_name_verified)
values
    ('patient-001', 'patient', '123456', '13800000000', '王小云', 'PATIENT', 'appointment:create,appointment:cancel,medical-record:read', true),
    ('doctor-001', 'doctor', '123456', '13900000000', '张医生', 'DOCTOR', 'appointment:read,appointment:skip,medical-record:write', true),
    ('admin-001', 'admin', '123456', '13700000000', '平台管理员', 'ADMIN', 'department:manage,doctor:manage,schedule:manage,dashboard:read', true)
on conflict (username) do nothing;

