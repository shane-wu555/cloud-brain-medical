-- ══════════════════════════════════════════════════════════════════
-- 医护员工 auth 账号（密码：abc12345）
-- username = id = employee_no（工号即身份，工号即登录凭证）
--
-- 工号规则：DDDDSSSS
--   0001xxxx 神经内科   0002xxxx 全科医学   0003xxxx 骨科
--   0004xxxx 心内科     0005xxxx 消化内科   0006xxxx 呼吸内科
--   0007xxxx 内分泌科   0008xxxx 神经外科   0009xxxx 皮肤科
--   0100xxxx 影像检查科 0200xxxx 检验科     0300xxxx 处置科
--   0400xxxx 药房       0500xxxx 收费处     0900xxxx 系统管理
-- ══════════════════════════════════════════════════════════════════
insert into user_account (id, username, password, name, role) values
  -- ── 神经内科（0001xxxx）──────────────────────────────────────────
  ('00010001','00010001','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','张医生','OUTPATIENT_DOCTOR'),
  ('00010002','00010002','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','刘医生','OUTPATIENT_DOCTOR'),
  ('00010003','00010003','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','王医生','OUTPATIENT_DOCTOR'),
  -- ── 全科医学（0002xxxx）──────────────────────────────────────────
  ('00020001','00020001','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','陈医生','OUTPATIENT_DOCTOR'),
  ('00020002','00020002','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','黄医生','OUTPATIENT_DOCTOR'),
  -- ── 骨科（0003xxxx）─────────────────────────────────────────────
  ('00030001','00030001','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','孙医生','OUTPATIENT_DOCTOR'),
  ('00030002','00030002','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','林医生','OUTPATIENT_DOCTOR'),
  -- ── 心内科（0004xxxx）───────────────────────────────────────────
  ('00040001','00040001','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','吴医生','OUTPATIENT_DOCTOR'),
  ('00040002','00040002','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','郑医生','OUTPATIENT_DOCTOR'),
  -- ── 消化内科（0005xxxx）──────────────────────────────────────────
  ('00050001','00050001','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','冯医生','OUTPATIENT_DOCTOR'),
  ('00050002','00050002','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','蒋医生','OUTPATIENT_DOCTOR'),
  -- ── 呼吸内科（0006xxxx）──────────────────────────────────────────
  ('00060001','00060001','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','韩医生','OUTPATIENT_DOCTOR'),
  ('00060002','00060002','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','杨医生','OUTPATIENT_DOCTOR'),
  -- ── 内分泌科（0007xxxx）──────────────────────────────────────────
  ('00070001','00070001','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','朱医生','OUTPATIENT_DOCTOR'),
  -- ── 神经外科（0008xxxx）──────────────────────────────────────────
  ('00080001','00080001','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','曹医生','OUTPATIENT_DOCTOR'),
  ('00080002','00080002','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','魏医生','OUTPATIENT_DOCTOR'),
  -- ── 影像/检查科（0100xxxx）──────────────────────────────────────
  ('01000001','01000001','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','李影医','CHECK_DOCTOR'),
  ('01000002','01000002','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','吴影医','CHECK_DOCTOR'),
  ('01000003','01000003','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','马影医','CHECK_DOCTOR'),
  ('01000004','01000004','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','胡影医','CHECK_DOCTOR'),
  ('01000005','01000005','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','杜影医','CHECK_DOCTOR'),
  -- ── 检验科（0200xxxx）───────────────────────────────────────────
  ('02000001','02000001','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','王技师','LAB_DOCTOR'),
  ('02000002','02000002','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','钱技师','LAB_DOCTOR'),
  ('02000003','02000003','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','沈技师','LAB_DOCTOR'),
  ('02000004','02000004','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','秦技师','LAB_DOCTOR'),
  ('02000005','02000005','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','尤技师','LAB_DOCTOR'),
  -- ── 处置科（0300xxxx）───────────────────────────────────────────
  ('03000001','03000001','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','赵护师','DISPOSAL_DOCTOR'),
  ('03000002','03000002','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','周护师','DISPOSAL_DOCTOR'),
  ('03000003','03000003','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','徐护师','DISPOSAL_DOCTOR'),
  ('03000004','03000004','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','许护师','DISPOSAL_DOCTOR'),
  ('03000005','03000005','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','何护师','DISPOSAL_DOCTOR'),
  -- ── 药房（0400xxxx）─────────────────────────────────────────────
  ('04000001','04000001','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','林药师','PHARMACY_STAFF'),
  ('04000002','04000002','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','吕药师','PHARMACY_STAFF'),
  -- ── 收费处（0500xxxx）───────────────────────────────────────────
  ('05000001','05000001','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','收费员甲','CASHIER'),
  ('05000002','05000002','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','收费员乙','CASHIER')
on conflict (id) do nothing;
