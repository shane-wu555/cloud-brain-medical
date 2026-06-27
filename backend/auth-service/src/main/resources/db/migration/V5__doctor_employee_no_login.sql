insert into user_account (id, username, password, phone, name, role, permissions, real_name_verified)
values
    ('doctor-003', 'D0003', '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13900000003', 'General Outpatient Doctor', 'OUTPATIENT_DOCTOR', 'appointment:read,appointment:skip,medical-record:read,medical-record:write,medical-order:create,prescription:create', true)
on conflict (id) do update
set username = excluded.username,
    password = excluded.password,
    phone = excluded.phone,
    name = excluded.name,
    role = excluded.role,
    permissions = excluded.permissions,
    real_name_verified = excluded.real_name_verified;

update user_account
set username = case id
    when 'doctor-001' then 'D0001'
    when 'doctor-check-001' then 'D0002'
    when 'doctor-003' then 'D0003'
    when 'doctor-lab-001' then 'L0001'
    when 'doctor-disposal-001' then 'T0001'
    when 'doctor-pharmacy-001' then 'P0001'
    else username
end
where id in ('doctor-001', 'doctor-check-001', 'doctor-003', 'doctor-lab-001', 'doctor-disposal-001', 'doctor-pharmacy-001');
