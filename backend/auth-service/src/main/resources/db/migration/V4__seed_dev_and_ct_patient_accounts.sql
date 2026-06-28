-- ══════════════════════════════════════════════════════════════════════
-- V4: 为 DevQueueSeeder（pat-dev-*）和 V11 CT患者（pat-ct-*）
--     补全 auth.user_account 记录，确保挂号数据有对应用户账号。
--     密码均为 abc12345（BCrypt）。
-- ══════════════════════════════════════════════════════════════════════

insert into user_account (id, username, password, phone, name, role, permissions, real_name_verified, employee_no, created_at)
values
  -- ── Dev 患者（神经内科测试队列）──
  ('pat-dev-001','13801000001','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13801000001','刘建国','PATIENT','appointment:create,appointment:cancel,medical-record:read',true,null,now()),
  ('pat-dev-002','13801000002','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13801000002','张秀英','PATIENT','appointment:create,appointment:cancel,medical-record:read',true,null,now()),
  ('pat-dev-003','13801000003','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13801000003','王大力','PATIENT','appointment:create,appointment:cancel,medical-record:read',true,null,now()),
  ('pat-dev-004','13801000004','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13801000004','陈小梅','PATIENT','appointment:create,appointment:cancel,medical-record:read',true,null,now()),
  ('pat-dev-005','13801000005','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13801000005','李明远','PATIENT','appointment:create,appointment:cancel,medical-record:read',true,null,now()),
  ('pat-dev-006','13801000006','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13801000006','赵晓燕','PATIENT','appointment:create,appointment:cancel,medical-record:read',true,null,now()),
  ('pat-dev-007','13801000007','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13801000007','孙志强','PATIENT','appointment:create,appointment:cancel,medical-record:read',true,null,now()),
  ('pat-dev-008','13801000008','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13801000008','周芳芳','PATIENT','appointment:create,appointment:cancel,medical-record:read',true,null,now()),
  ('pat-dev-009','13801000009','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13801000009','吴德强','PATIENT','appointment:create,appointment:cancel,medical-record:read',true,null,now()),
  ('pat-dev-010','13801000010','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13801000010','郑玉兰','PATIENT','appointment:create,appointment:cancel,medical-record:read',true,null,now()),
  ('pat-dev-011','13801000011','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13801000011','林小华','PATIENT','appointment:create,appointment:cancel,medical-record:read',true,null,now()),
  ('pat-dev-012','13801000012','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13801000012','黄建平','PATIENT','appointment:create,appointment:cancel,medical-record:read',true,null,now()),
  ('pat-dev-013','13801000013','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13801000013','陈晓丽','PATIENT','appointment:create,appointment:cancel,medical-record:read',true,null,now()),
  ('pat-dev-014','13801000014','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13801000014','周明辉','PATIENT','appointment:create,appointment:cancel,medical-record:read',true,null,now()),
  ('pat-dev-015','13801000015','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13801000015','吴晓红','PATIENT','appointment:create,appointment:cancel,medical-record:read',true,null,now()),
  -- ── CT验证患者（头部检查场景）──
  ('pat-ct-001','13901000001','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13901000001','李建军','PATIENT','appointment:create,appointment:cancel,medical-record:read',true,null,now()),
  ('pat-ct-002','13901000002','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13901000002','周红英','PATIENT','appointment:create,appointment:cancel,medical-record:read',true,null,now()),
  ('pat-ct-003','13901000003','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13901000003','张永军','PATIENT','appointment:create,appointment:cancel,medical-record:read',true,null,now()),
  ('pat-ct-004','13901000004','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13901000004','朱晓燕','PATIENT','appointment:create,appointment:cancel,medical-record:read',true,null,now()),
  ('pat-ct-005','13901000005','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13901000005','陈志强','PATIENT','appointment:create,appointment:cancel,medical-record:read',true,null,now()),
  ('pat-ct-006','13901000006','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13901000006','王丽华','PATIENT','appointment:create,appointment:cancel,medical-record:read',true,null,now()),
  ('pat-ct-007','13901000007','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13901000007','孙建国','PATIENT','appointment:create,appointment:cancel,medical-record:read',true,null,now()),
  ('pat-ct-008','13901000008','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13901000008','刘芳','PATIENT','appointment:create,appointment:cancel,medical-record:read',true,null,now()),
  ('pat-ct-009','13901000009','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13901000009','钱大明','PATIENT','appointment:create,appointment:cancel,medical-record:read',true,null,now()),
  ('pat-ct-010','13901000010','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13901000010','赵小燕','PATIENT','appointment:create,appointment:cancel,medical-record:read',true,null,now()),
  ('pat-ct-011','13901000011','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13901000011','吴小红','PATIENT','appointment:create,appointment:cancel,medical-record:read',true,null,now()),
  ('pat-ct-012','13901000012','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13901000012','林美华','PATIENT','appointment:create,appointment:cancel,medical-record:read',true,null,now()),
  ('pat-ct-013','13901000013','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13901000013','徐志明','PATIENT','appointment:create,appointment:cancel,medical-record:read',true,null,now()),
  ('pat-ct-014','13901000014','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13901000014','黄建国','PATIENT','appointment:create,appointment:cancel,medical-record:read',true,null,now()),
  ('pat-ct-015','13901000015','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13901000015','郑建华','PATIENT','appointment:create,appointment:cancel,medical-record:read',true,null,now())
on conflict (id) do update
  set real_name_verified = true,
      name               = excluded.name;
