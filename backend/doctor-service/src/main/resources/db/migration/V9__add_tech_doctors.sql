-- ══════════════════════════════════════════════════════════════════════
-- V9: 补充检验科、处置科科室及对应医技医生。
--     id = auth.user_account.id，使医技工作台账号与 doctor 表一一对应。
--
-- 工号规则：科室编码(4位) + 科室内序号(4位)
--   0001xxxx → 神经内科    0002xxxx → （预留）
--   0003xxxx → 检查/影像科  0004xxxx → 全科医学
--   0005xxxx → 检验科       0006xxxx → 处置科
--
-- 使用 WHERE NOT EXISTS 同时防止 id 与 employee_no 双重冲突，
-- 若对应工号已通过其他途径创建则跳过，不报错。
-- ══════════════════════════════════════════════════════════════════════

insert into department (id, name, description) values
    ('dept-lab',      '检验科', '血常规、生化、免疫等检验项目'),
    ('dept-disposal', '处置科', '静脉输液、换药、神经康复等处置项目')
on conflict (id) do nothing;

-- 检验科医生（LAB_DOCTOR）：工号 0005xxxx
insert into doctor (id, name, title, department_id, role_type, specialty, employee_no)
select 'doctor-004', '王医生', '主管技师', 'dept-lab', 'LAB_DOCTOR', '血常规、生化检验', '00050001'
where not exists (select 1 from doctor where id = 'doctor-004')
  and not exists (select 1 from doctor where employee_no = '00050001');

insert into doctor (id, name, title, department_id, role_type, specialty, employee_no)
select 'doctor-007', '钱医生', '技师', 'dept-lab', 'LAB_DOCTOR', '血常规、生化检验', '00050002'
where not exists (select 1 from doctor where id = 'doctor-007')
  and not exists (select 1 from doctor where employee_no = '00050002');

-- 处置科医生（DISPOSAL_DOCTOR）：工号 0006xxxx
insert into doctor (id, name, title, department_id, role_type, specialty, employee_no)
select 'doctor-005', '赵医生', '主管护师', 'dept-disposal', 'DISPOSAL_DOCTOR', '静脉输液、换药', '00060001'
where not exists (select 1 from doctor where id = 'doctor-005')
  and not exists (select 1 from doctor where employee_no = '00060001');

insert into doctor (id, name, title, department_id, role_type, specialty, employee_no)
select 'doctor-008', '周医生', '护师', 'dept-disposal', 'DISPOSAL_DOCTOR', '静脉输液', '00060002'
where not exists (select 1 from doctor where id = 'doctor-008')
  and not exists (select 1 from doctor where employee_no = '00060002');

-- 检查科第二名医生（CHECK_DOCTOR）：工号 0003xxxx 续号
insert into doctor (id, name, title, department_id, role_type, specialty, employee_no)
select 'doctor-006', '吴医生', '主治医师', 'dept-imaging', 'CHECK_DOCTOR', '头部CT影像', '00030002'
where not exists (select 1 from doctor where id = 'doctor-006')
  and not exists (select 1 from doctor where employee_no = '00030002');
