-- ══════════════════════════════════════════════════════════════════════
-- V5: 为线下挂号的测试患者补全 patient_profile
--
-- 设计说明：
--   这批患者是线下建档患者（DevQueueSeeder / V11 插入的挂号记录），
--   他们没有 App 账号，account_id = NULL 是正确的。
--   auth V4 创建了同名 user_account 仅供测试人员登录调试使用，
--   但两者之间没有预先绑定——如需绑定须走实名认证流程。
--   user_id 保留历史非空约束，设为与 id 相同（与 createOffline 行为一致）。
-- ══════════════════════════════════════════════════════════════════════

insert into patient_profile (id, user_id, account_id, phone, name, gender, birth_date, id_type, id_number, real_name_verified, verified_at, created_at, updated_at)
values
  -- ── Dev 患者（线下建档，account_id = NULL）──
  ('pat-dev-001','pat-dev-001',NULL,'13801000001','刘建国','MALE',  '1968-03-15','ID_CARD','110101196803150013',true,now(),now(),now()),
  ('pat-dev-002','pat-dev-002',NULL,'13801000002','张秀英','FEMALE','1975-07-22','ID_CARD','110101197507220024',true,now(),now(),now()),
  ('pat-dev-003','pat-dev-003',NULL,'13801000003','王大力','MALE',  '1982-11-08','ID_CARD','110101198211080035',true,now(),now(),now()),
  ('pat-dev-004','pat-dev-004',NULL,'13801000004','陈小梅','FEMALE','1960-05-30','ID_CARD','110101196005300046',true,now(),now(),now()),
  ('pat-dev-005','pat-dev-005',NULL,'13801000005','李明远','MALE',  '1955-09-12','ID_CARD','110101195509120057',true,now(),now(),now()),
  ('pat-dev-006','pat-dev-006',NULL,'13801000006','赵晓燕','FEMALE','1990-02-18','ID_CARD','110101199002180068',true,now(),now(),now()),
  ('pat-dev-007','pat-dev-007',NULL,'13801000007','孙志强','MALE',  '1952-06-25','ID_CARD','110101195206250079',true,now(),now(),now()),
  ('pat-dev-008','pat-dev-008',NULL,'13801000008','周芳芳','FEMALE','1978-12-03','ID_CARD','110101197812030081',true,now(),now(),now()),
  ('pat-dev-009','pat-dev-009',NULL,'13801000009','吴德强','MALE',  '1948-04-17','ID_CARD','110101194804170092',true,now(),now(),now()),
  ('pat-dev-010','pat-dev-010',NULL,'13801000010','郑玉兰','FEMALE','1963-08-29','ID_CARD','110101196308290103',true,now(),now(),now()),
  ('pat-dev-011','pat-dev-011',NULL,'13801000011','林小华','MALE',  '1985-01-14','ID_CARD','110101198501140114',true,now(),now(),now()),
  ('pat-dev-012','pat-dev-012',NULL,'13801000012','黄建平','MALE',  '1959-10-06','ID_CARD','110101195910060125',true,now(),now(),now()),
  ('pat-dev-013','pat-dev-013',NULL,'13801000013','陈晓丽','FEMALE','1988-03-21','ID_CARD','110101198803210136',true,now(),now(),now()),
  ('pat-dev-014','pat-dev-014',NULL,'13801000014','周明辉','MALE',  '1970-07-09','ID_CARD','110101197007090147',true,now(),now(),now()),
  ('pat-dev-015','pat-dev-015',NULL,'13801000015','吴晓红','FEMALE','1995-11-27','ID_CARD','110101199511270158',true,now(),now(),now()),
  -- ── CT验证患者（线下建档，account_id = NULL）──
  ('pat-ct-001','pat-ct-001',NULL,'13901000001','李建军','MALE',  '1980-04-12','ID_CARD','110101198004120211',true,now(),now(),now()),
  ('pat-ct-002','pat-ct-002',NULL,'13901000002','周红英','FEMALE','1965-09-30','ID_CARD','110101196509300222',true,now(),now(),now()),
  ('pat-ct-003','pat-ct-003',NULL,'13901000003','张永军','MALE',  '1972-02-18','ID_CARD','110101197202180233',true,now(),now(),now()),
  ('pat-ct-004','pat-ct-004',NULL,'13901000004','朱晓燕','FEMALE','1983-06-05','ID_CARD','110101198306050244',true,now(),now(),now()),
  ('pat-ct-005','pat-ct-005',NULL,'13901000005','陈志强','MALE',  '1976-11-22','ID_CARD','110101197611220255',true,now(),now(),now()),
  ('pat-ct-006','pat-ct-006',NULL,'13901000006','王丽华','FEMALE','1969-03-08','ID_CARD','110101196903080266',true,now(),now(),now()),
  ('pat-ct-007','pat-ct-007',NULL,'13901000007','孙建国','MALE',  '1958-07-14','ID_CARD','110101195807140277',true,now(),now(),now()),
  ('pat-ct-008','pat-ct-008',NULL,'13901000008','刘芳',  'FEMALE','1991-12-01','ID_CARD','110101199112010288',true,now(),now(),now()),
  ('pat-ct-009','pat-ct-009',NULL,'13901000009','钱大明','MALE',  '1955-05-19','ID_CARD','110101195505190299',true,now(),now(),now()),
  ('pat-ct-010','pat-ct-010',NULL,'13901000010','赵小燕','FEMALE','1987-08-27','ID_CARD','110101198708270301',true,now(),now(),now()),
  ('pat-ct-011','pat-ct-011',NULL,'13901000011','吴小红','FEMALE','1993-01-15','ID_CARD','110101199301150312',true,now(),now(),now()),
  ('pat-ct-012','pat-ct-012',NULL,'13901000012','林美华','FEMALE','1950-10-23','ID_CARD','110101195010230323',true,now(),now(),now()),
  ('pat-ct-013','pat-ct-013',NULL,'13901000013','徐志明','MALE',  '1962-04-07','ID_CARD','110101196204070334',true,now(),now(),now()),
  ('pat-ct-014','pat-ct-014',NULL,'13901000014','黄建国','MALE',  '1974-09-16','ID_CARD','110101197409160345',true,now(),now(),now()),
  ('pat-ct-015','pat-ct-015',NULL,'13901000015','郑建华','MALE',  '1967-12-30','ID_CARD','110101196712300356',true,now(),now(),now())
on conflict (id) do update
  set real_name_verified = true,
      verified_at        = coalesce(patient_profile.verified_at, now());

-- 注意：不插入 account_patient_binding。
-- 绑定只能通过用户主动完成实名认证流程建立，不在此处预设。
