-- ══════════════════════════════════════════════════════════════════════
-- V5: 为所有医护人员补全 auth.user_account。
--     id = doctor-service.doctor.id，全院统一身份，密码 abc12345（BCrypt）。
--     username = employee_no（工号登录）。
-- ══════════════════════════════════════════════════════════════════════

insert into user_account (id, username, password, phone, name, role, permissions, real_name_verified, employee_no, created_at)
values
  -- ── 门诊医生 ───────────────────────────────────────────────────────
  ('doctor-001', '00010001', '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.',
   '13700000101', '张医生', 'OUTPATIENT_DOCTOR',
   'appointment:read,medical-record:write,medical-order:create,prescription:write',
   true, '00010001', now()),

  ('doctor-003', '00040001', '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.',
   '13700000301', '陈医生', 'OUTPATIENT_DOCTOR',
   'appointment:read,medical-record:write,medical-order:create,prescription:write',
   true, '00040001', now()),

  -- ── 检查科（CHECK_DOCTOR）─────────────────────────────────────────
  ('doctor-002', '00030001', '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.',
   '13700000201', '李医生', 'CHECK_DOCTOR',
   'medical-order:read,medical-order:execute',
   true, '00030001', now()),

  ('doctor-006', '00070001', '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.',
   '13700000601', '吴医生', 'CHECK_DOCTOR',
   'medical-order:read,medical-order:execute',
   true, '00070001', now()),

  -- ── 检验科（LAB_DOCTOR）──────────────────────────────────────────
  ('doctor-004', '00050001', '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.',
   '13700000401', '王医生', 'LAB_DOCTOR',
   'medical-order:read,medical-order:execute',
   true, '00050001', now()),

  ('doctor-007', '00080001', '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.',
   '13700000701', '钱医生', 'LAB_DOCTOR',
   'medical-order:read,medical-order:execute',
   true, '00080001', now()),

  -- ── 处置科（DISPOSAL_DOCTOR）─────────────────────────────────────
  ('doctor-005', '00060001', '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.',
   '13700000501', '赵医生', 'DISPOSAL_DOCTOR',
   'medical-order:read,medical-order:execute',
   true, '00060001', now()),

  ('doctor-008', '00090001', '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.',
   '13700000801', '周医生', 'DISPOSAL_DOCTOR',
   'medical-order:read,medical-order:execute',
   true, '00090001', now()),

  -- ── CASHIER 收费员 ────────────────────────────────────────────────
  ('cashier-001', 'cashier01', '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.',
   '13700001001', '收费员', 'CASHIER',
   'medical-order:read,payment:create',
   true, null, now())

on conflict (id) do update
  set username          = excluded.username,
      name              = excluded.name,
      role              = excluded.role,
      real_name_verified = true,
      employee_no       = excluded.employee_no;
