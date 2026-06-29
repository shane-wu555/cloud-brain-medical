-- ══════════════════════════════════════════════════════════════════════
-- V7: 确保所有医护人员 auth 账号正确存在（id = doctor.id，username = 工号）。
--
-- 工号规则（同 doctor-service V9）：
--   0001xxxx 神经内科 / 0003xxxx 检查科 / 0004xxxx 全科医学
--   0005xxxx 检验科   / 0006xxxx 处置科
--
-- 旧 V5 可能写入错误账号（workspace-check-002 等），本迁移先清理再重建。
-- ══════════════════════════════════════════════════════════════════════

-- 清理旧 V5 写入的占位账号
delete from user_account
where id in (
    'doctor-check-001',
    'workspace-check-002', 'workspace-check-003',
    'workspace-lab-002',   'workspace-lab-003',
    'workspace-disposal-002', 'workspace-disposal-003'
);

-- 门诊医生
insert into user_account (id, username, password, phone, name, role, permissions, real_name_verified, employee_no, created_at)
values
  ('doctor-001', '00010001', '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.',
   '13700000101', '张医生', 'OUTPATIENT_DOCTOR',
   'appointment:read,medical-record:write,medical-order:create,prescription:write',
   true, '00010001', now()),
  ('doctor-003', '00040001', '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.',
   '13700000301', '陈医生', 'OUTPATIENT_DOCTOR',
   'appointment:read,medical-record:write,medical-order:create,prescription:write',
   true, '00040001', now())
on conflict (id) do update
  set username = excluded.username, name = excluded.name,
      role = excluded.role, real_name_verified = true, employee_no = excluded.employee_no;

-- 检查科（0003xxxx）
insert into user_account (id, username, password, phone, name, role, permissions, real_name_verified, employee_no, created_at)
values
  ('doctor-002', '00030001', '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.',
   '13700000201', '李医生', 'CHECK_DOCTOR',
   'medical-order:read,medical-order:execute', true, '00030001', now()),
  ('doctor-006', '00030002', '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.',
   '13700000202', '吴医生', 'CHECK_DOCTOR',
   'medical-order:read,medical-order:execute', true, '00030002', now())
on conflict (id) do update
  set username = excluded.username, name = excluded.name,
      role = excluded.role, real_name_verified = true, employee_no = excluded.employee_no;

-- 检验科（0005xxxx）：工号可能已存在，跳过重复 employee_no
insert into user_account (id, username, password, phone, name, role, permissions, real_name_verified, employee_no, created_at)
select 'doctor-004', '00050001', '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.',
       '13700000401', '王医生', 'LAB_DOCTOR',
       'medical-order:read,medical-order:execute', true, '00050001', now()
where not exists (select 1 from user_account where id = 'doctor-004')
  and not exists (select 1 from user_account where employee_no = '00050001');

insert into user_account (id, username, password, phone, name, role, permissions, real_name_verified, employee_no, created_at)
select 'doctor-007', '00050002', '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.',
       '13700000402', '钱医生', 'LAB_DOCTOR',
       'medical-order:read,medical-order:execute', true, '00050002', now()
where not exists (select 1 from user_account where id = 'doctor-007')
  and not exists (select 1 from user_account where employee_no = '00050002');

-- 处置科（0006xxxx）
insert into user_account (id, username, password, phone, name, role, permissions, real_name_verified, employee_no, created_at)
select 'doctor-005', '00060001', '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.',
       '13700000501', '赵医生', 'DISPOSAL_DOCTOR',
       'medical-order:read,medical-order:execute', true, '00060001', now()
where not exists (select 1 from user_account where id = 'doctor-005')
  and not exists (select 1 from user_account where employee_no = '00060001');

insert into user_account (id, username, password, phone, name, role, permissions, real_name_verified, employee_no, created_at)
select 'doctor-008', '00060002', '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.',
       '13700000502', '周医生', 'DISPOSAL_DOCTOR',
       'medical-order:read,medical-order:execute', true, '00060002', now()
where not exists (select 1 from user_account where id = 'doctor-008')
  and not exists (select 1 from user_account where employee_no = '00060002');

-- 收费员
insert into user_account (id, username, password, phone, name, role, permissions, real_name_verified, employee_no, created_at)
values ('cashier-001', 'cashier01', '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.',
        '13700001001', '收费员', 'CASHIER', 'medical-order:read,payment:create', true, null, now())
on conflict (id) do update
  set username = excluded.username, name = excluded.name,
      role = excluded.role, real_name_verified = true;
