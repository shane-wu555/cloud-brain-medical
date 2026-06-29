-- ══════════════════════════════════════════════════════════════════════
-- V7: 确保所有医护人员 auth 账号正确存在（id = doctor.id，username = 工号）。
--     全部使用 WHERE NOT EXISTS 同时防止 id 与 employee_no 双重冲突，
--     若对应工号已由其他账号占用则安全跳过。
-- ══════════════════════════════════════════════════════════════════════

-- 清理旧 V5 写入的占位账号（工作室 ID 冒充人员 ID）
delete from user_account
where id in (
    'doctor-check-001',
    'workspace-check-002', 'workspace-check-003',
    'workspace-lab-002',   'workspace-lab-003',
    'workspace-disposal-002', 'workspace-disposal-003'
);

-- ── 门诊医生 ───────────────────────────────────────────────────────────
insert into user_account (id, username, password, phone, name, role, permissions, real_name_verified, employee_no, created_at)
select 'doctor-001','00010001','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13700000101','张医生','OUTPATIENT_DOCTOR','appointment:read,medical-record:write,medical-order:create,prescription:write',true,'00010001',now()
where not exists (select 1 from user_account where id='doctor-001') and not exists (select 1 from user_account where employee_no='00010001');

insert into user_account (id, username, password, phone, name, role, permissions, real_name_verified, employee_no, created_at)
select 'doctor-003','00040001','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13700000301','陈医生','OUTPATIENT_DOCTOR','appointment:read,medical-record:write,medical-order:create,prescription:write',true,'00040001',now()
where not exists (select 1 from user_account where id='doctor-003') and not exists (select 1 from user_account where employee_no='00040001');

-- ── 检查科（0003xxxx）─────────────────────────────────────────────────
insert into user_account (id, username, password, phone, name, role, permissions, real_name_verified, employee_no, created_at)
select 'doctor-002','00030001','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13700000201','李医生','CHECK_DOCTOR','medical-order:read,medical-order:execute',true,'00030001',now()
where not exists (select 1 from user_account where id='doctor-002') and not exists (select 1 from user_account where employee_no='00030001');

insert into user_account (id, username, password, phone, name, role, permissions, real_name_verified, employee_no, created_at)
select 'doctor-006','00030002','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13700000202','吴医生','CHECK_DOCTOR','medical-order:read,medical-order:execute',true,'00030002',now()
where not exists (select 1 from user_account where id='doctor-006') and not exists (select 1 from user_account where employee_no='00030002');

-- ── 检验科（0005xxxx）─────────────────────────────────────────────────
insert into user_account (id, username, password, phone, name, role, permissions, real_name_verified, employee_no, created_at)
select 'doctor-004','00050001','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13700000401','王医生','LAB_DOCTOR','medical-order:read,medical-order:execute',true,'00050001',now()
where not exists (select 1 from user_account where id='doctor-004') and not exists (select 1 from user_account where employee_no='00050001');

insert into user_account (id, username, password, phone, name, role, permissions, real_name_verified, employee_no, created_at)
select 'doctor-007','00050002','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13700000402','钱医生','LAB_DOCTOR','medical-order:read,medical-order:execute',true,'00050002',now()
where not exists (select 1 from user_account where id='doctor-007') and not exists (select 1 from user_account where employee_no='00050002');

-- ── 处置科（0006xxxx）─────────────────────────────────────────────────
insert into user_account (id, username, password, phone, name, role, permissions, real_name_verified, employee_no, created_at)
select 'doctor-005','00060001','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13700000501','赵医生','DISPOSAL_DOCTOR','medical-order:read,medical-order:execute',true,'00060001',now()
where not exists (select 1 from user_account where id='doctor-005') and not exists (select 1 from user_account where employee_no='00060001');

insert into user_account (id, username, password, phone, name, role, permissions, real_name_verified, employee_no, created_at)
select 'doctor-008','00060002','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13700000502','周医生','DISPOSAL_DOCTOR','medical-order:read,medical-order:execute',true,'00060002',now()
where not exists (select 1 from user_account where id='doctor-008') and not exists (select 1 from user_account where employee_no='00060002');

-- ── 收费员 ────────────────────────────────────────────────────────────
insert into user_account (id, username, password, phone, name, role, permissions, real_name_verified, employee_no, created_at)
select 'cashier-001','cashier01','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.','13700001001','收费员','CASHIER','medical-order:read,payment:create',true,null,now()
where not exists (select 1 from user_account where id='cashier-001');
