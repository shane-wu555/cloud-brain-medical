-- ══════════════════════════════════════════════════════════════════════
-- V9: 补充检验科、处置科科室及对应医技医生。
--     id = auth.user_account.id，使医技工作台账号与 doctor 表一一对应。
-- ══════════════════════════════════════════════════════════════════════

insert into department (id, name, description) values
    ('dept-lab',      '检验科', '血常规、生化、免疫等检验项目'),
    ('dept-disposal', '处置科', '静脉输液、换药、神经康复等处置项目')
on conflict (id) do nothing;

-- 检验科医生（LAB_DOCTOR）
insert into doctor (id, name, title, department_id, role_type, specialty, employee_no) values
    ('doctor-004', '王医生', '主管技师',   'dept-lab',      'LAB_DOCTOR',      '血常规、生化检验', '00050001'),
    ('doctor-007', '钱医生', '技师',       'dept-lab',      'LAB_DOCTOR',      '血常规、生化检验', '00080001')
on conflict (id) do nothing;

-- 处置科医生（DISPOSAL_DOCTOR）
insert into doctor (id, name, title, department_id, role_type, specialty, employee_no) values
    ('doctor-005', '赵医生', '主管护师',   'dept-disposal', 'DISPOSAL_DOCTOR', '静脉输液、换药',   '00060001'),
    ('doctor-008', '周医生', '护师',       'dept-disposal', 'DISPOSAL_DOCTOR', '静脉输液',         '00090001')
on conflict (id) do nothing;

-- 检查科追加一名医生（第二检查室用）
insert into doctor (id, name, title, department_id, role_type, specialty, employee_no) values
    ('doctor-006', '吴医生', '主治医师',   'dept-imaging',  'CHECK_DOCTOR',    '头部CT影像',       '00070001')
on conflict (id) do nothing;
