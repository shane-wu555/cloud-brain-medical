-- ══════════════════════════════════════════════════════════════════════
-- V5: 补全医技人员 auth.user_account 记录。
--     密码均为 abc12345（BCrypt）。
--     id 与 medical_order_service.medical_technician.id 保持一致，
--     以满足跨服务外键约束（V4 中条件添加）。
-- ══════════════════════════════════════════════════════════════════════

insert into user_account (id, username, password, phone, name, role, permissions, real_name_verified, employee_no, created_at)
values
  -- ── 检查科（CHECK）──
  ('doctor-check-001',    'check001',    '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000201', '赵影像师',   'CHECK_DOCTOR',    'medical-order:read,medical-order:execute', true, 'D0002', now()),
  ('workspace-check-002', 'check002',    '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000204', '吴影像师',   'CHECK_DOCTOR',    'medical-order:read,medical-order:execute', true, 'D0003', now()),
  ('workspace-check-003', 'check003',    '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000205', '冯影像师',   'CHECK_DOCTOR',    'medical-order:read,medical-order:execute', true, 'D0004', now()),

  -- ── 检验科（LAB）──
  ('doctor-lab-001',      'lab001',      '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000202', '钱检验师',   'LAB_DOCTOR',      'medical-order:read,medical-order:execute', true, 'L0001', now()),
  ('workspace-lab-002',   'lab002',      '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000206', '郑检验师',   'LAB_DOCTOR',      'medical-order:read,medical-order:execute', true, 'L0002', now()),
  ('workspace-lab-003',   'lab003',      '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000207', '王急检师',   'LAB_DOCTOR',      'medical-order:read,medical-order:execute', true, 'L0003', now()),

  -- ── 处置科（DISPOSAL）──
  ('doctor-disposal-001', 'disposal001', '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000203', '孙处置师',   'DISPOSAL_DOCTOR', 'medical-order:read,medical-order:execute', true, 'T0001', now()),
  ('workspace-disposal-002','disposal002','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13700000208', '周处置师',   'DISPOSAL_DOCTOR', 'medical-order:read,medical-order:execute', true, 'T0002', now()),
  ('workspace-disposal-003','disposal003','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13700000209', '徐急处师',   'DISPOSAL_DOCTOR', 'medical-order:read,medical-order:execute', true, 'T0003', now())
on conflict (id) do update
  set real_name_verified = true,
      name               = excluded.name,
      role               = excluded.role,
      employee_no        = excluded.employee_no;
