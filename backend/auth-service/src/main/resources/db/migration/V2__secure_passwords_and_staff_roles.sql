update user_account
set password = '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.'
where password = '123456';

update user_account
set role = 'OUTPATIENT_DOCTOR',
    permissions = 'appointment:read,appointment:skip,medical-record:read,medical-record:write,medical-order:create,prescription:create'
where username = 'doctor';

insert into user_account (id, username, password, phone, name, role, permissions, real_name_verified)
values
    ('staff-cashier-001', 'cashier', '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000001', '窗口收费员', 'CASHIER', 'appointment:create-offline,appointment:cancel,payment:create,refund:create', true),
    ('doctor-check-001', 'check-doctor', '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000002', '检查医生', 'CHECK_DOCTOR', 'medical-order:read,medical-order:execute,report:confirm', true),
    ('doctor-lab-001', 'lab-doctor', '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000003', '检验医生', 'LAB_DOCTOR', 'medical-order:read,medical-order:execute,report:confirm', true),
    ('doctor-disposal-001', 'disposal-doctor', '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000004', '处置医生', 'DISPOSAL_DOCTOR', 'medical-order:read,medical-order:execute', true),
    ('doctor-pharmacy-001', 'pharmacy-doctor', '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000005', '药房医生', 'PHARMACY_DOCTOR', 'prescription:read,dispense:create,refund:create,inventory:manage', true)
on conflict (username) do update set
    password = excluded.password,
    role = excluded.role,
    permissions = excluded.permissions;
