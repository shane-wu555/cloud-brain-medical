begin;

-- ================================================================
-- seed-departments-doctors-schedules.sql
--
-- 初始化科室、医护人员账号（含工号）、医生档案、门诊排班。
--
-- 工号规则（8位纯数字）：
--   科室前缀（前4位）+ 科内序号（后4位）
--   0001xxxx 神经内科   | 0002xxxx 神经外科   | 0003xxxx 检查科
--   0004xxxx 全科医学   | 0005xxxx 康复科     | 0006xxxx 心血管内科
--   0007xxxx 骨科       | 0008xxxx 妇科       | 0009xxxx 内分泌科
--   0010xxxx 呼吸内科   | 0011xxxx 胸外科
--   0012xxxx 检验科     | 0013xxxx 药房       | 0014xxxx 处置室
--   0015xxxx 收费处
--
-- 患者：手机号登录，无工号。
-- 管理员：单一统一账号 admin，无工号。
-- 所有医护人员：工号登录，employee_no 不为 NULL。
-- ================================================================

-- ────────────────────────────────────────────────────────────────
-- 1. 科室
-- ────────────────────────────────────────────────────────────────
insert into doctor.department (id, name, description, active) values
    ('dept-neuro',     '神经内科',   '头痛、眩晕、脑血管疾病及神经系统慢病管理',   true),
    ('dept-neurosurg', '神经外科',   '颅脑肿瘤、脑血管手术、脑积水及术后随诊门诊', true),
    ('dept-imaging',   '检查科',     'B超、CT/MRI 及各类医学影像检查',             true),
    ('dept-general',   '全科医学',   '常见病初诊、慢病随诊及健康咨询',             true),
    ('dept-rehab',     '康复科',     '针灸、理疗、换药及术后康复随诊',             true),
    ('dept-cardio',    '心血管内科', '高血压、冠心病、心律失常及心衰管理',         true),
    ('dept-ortho',     '骨科',       '骨折、关节病、脊柱及运动损伤',               true),
    ('dept-gyne',      '妇科',       '月经病、妇科炎症、孕期随诊及更年期管理',     true),
    ('dept-endo',      '内分泌科',   '糖尿病、甲状腺疾病及代谢综合征管理',         true),
    ('dept-resp',      '呼吸内科',   '肺炎、慢阻肺、哮喘、肺结节及肺癌化疗随诊',  true),
    ('dept-thoracic',  '胸外科',     '肺部手术、胸腔积液、食管疾病及胸外术后随访', true),
    ('dept-lab',       '检验科',     '血常规、生化、微生物等临床检验',             true),
    ('dept-pharmacy',  '药房',       '门诊取药、处方审核、用药咨询',               true),
    ('dept-disposal',  '处置室',     '换药、注射、静脉输液等处置操作',             true),
    ('dept-cashier',   '收费处',     '门诊挂号收费及退费',                         true)
on conflict (id) do update
    set name        = excluded.name,
        description = excluded.description,
        active      = excluded.active;

-- ────────────────────────────────────────────────────────────────
-- 2. 医护人员账号 (auth.user_account)
-- username = 内部标识（不变）；employee_no = 8位工号（登录凭证）
-- BCrypt hash 对应密码: abc12345
-- ────────────────────────────────────────────────────────────────
insert into auth.user_account
    (id, username, password, phone, name, role, permissions, real_name_verified, employee_no, created_at)
values
    -- ── 神经内科（门诊）──
    ('doctor-001',         'doctor-001',         '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000030', '张医生', 'OUTPATIENT_DOCTOR', 'appointment:read,appointment:skip,medical-record:read,medical-record:write,medical-order:create,prescription:create', true, '00010001', now()),
    ('doctor-neuro-02',    'doctor-neuro-02',    '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000031', '王医生', 'OUTPATIENT_DOCTOR', 'appointment:read,appointment:skip,medical-record:read,medical-record:write,medical-order:create,prescription:create', true, '00010002', now()),
    ('doctor-neuro-03',    'doctor-neuro-03',    '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000032', '刘医生', 'OUTPATIENT_DOCTOR', 'appointment:read,appointment:skip,medical-record:read,medical-record:write,medical-order:create,prescription:create', true, '00010003', now()),
    -- ── 神经外科（门诊）──
    ('doctor-neurosurg-01','doctor-neurosurg-01','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000045', '林医生', 'OUTPATIENT_DOCTOR', 'appointment:read,appointment:skip,medical-record:read,medical-record:write,medical-order:create,prescription:create', true, '00020001', now()),
    ('doctor-neurosurg-02','doctor-neurosurg-02','$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000046', '何医生', 'OUTPATIENT_DOCTOR', 'appointment:read,appointment:skip,medical-record:read,medical-record:write,medical-order:create,prescription:create', true, '00020002', now()),
    -- ── 检查科（CHECK_DOCTOR）──
    ('doctor-002',         'doctor-002',         '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000020', '李医生', 'CHECK_DOCTOR', 'medical-order:read,medical-order:execute,report:confirm', true, '00030001', now()),
    ('doctor-imaging-02',  'doctor-imaging-02',  '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000044', '高医生', 'CHECK_DOCTOR', 'medical-order:read,medical-order:execute,report:confirm', true, '00030002', now()),
    ('doctor-imaging-03',  'doctor-imaging-03',  '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000054', '马医生', 'CHECK_DOCTOR', 'medical-order:read,medical-order:execute,report:confirm', true, '00030003', now()),
    -- ── 全科医学（门诊）──
    ('doctor-003',         'doctor-003',         '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000010', '陈医生', 'OUTPATIENT_DOCTOR', 'appointment:read,appointment:skip,medical-record:read,medical-record:write,medical-order:create,prescription:create', true, '00040001', now()),
    ('doctor-general-02',  'doctor-general-02',  '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000033', '赵医生', 'OUTPATIENT_DOCTOR', 'appointment:read,appointment:skip,medical-record:read,medical-record:write,medical-order:create,prescription:create', true, '00040002', now()),
    ('doctor-general-03',  'doctor-general-03',  '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000034', '孙医生', 'OUTPATIENT_DOCTOR', 'appointment:read,appointment:skip,medical-record:read,medical-record:write,medical-order:create,prescription:create', true, '00040003', now()),
    -- ── 康复科（门诊）──
    ('doctor-rehab-01',    'doctor-rehab-01',    '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000035', '周医生', 'OUTPATIENT_DOCTOR', 'appointment:read,appointment:skip,medical-record:read,medical-record:write,medical-order:create,prescription:create', true, '00050001', now()),
    -- ── 心血管内科（门诊）──
    ('doctor-cardio-01',   'doctor-cardio-01',   '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000036', '吴医生', 'OUTPATIENT_DOCTOR', 'appointment:read,appointment:skip,medical-record:read,medical-record:write,medical-order:create,prescription:create', true, '00060001', now()),
    ('doctor-cardio-02',   'doctor-cardio-02',   '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000037', '郑医生', 'OUTPATIENT_DOCTOR', 'appointment:read,appointment:skip,medical-record:read,medical-record:write,medical-order:create,prescription:create', true, '00060002', now()),
    -- ── 骨科（门诊）──
    ('doctor-ortho-01',    'doctor-ortho-01',    '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000038', '冯医生', 'OUTPATIENT_DOCTOR', 'appointment:read,appointment:skip,medical-record:read,medical-record:write,medical-order:create,prescription:create', true, '00070001', now()),
    ('doctor-ortho-02',    'doctor-ortho-02',    '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000039', '魏医生', 'OUTPATIENT_DOCTOR', 'appointment:read,appointment:skip,medical-record:read,medical-record:write,medical-order:create,prescription:create', true, '00070002', now()),
    -- ── 妇科（门诊）──
    ('doctor-gyne-01',     'doctor-gyne-01',     '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000040', '蒋医生', 'OUTPATIENT_DOCTOR', 'appointment:read,appointment:skip,medical-record:read,medical-record:write,medical-order:create,prescription:create', true, '00080001', now()),
    ('doctor-gyne-02',     'doctor-gyne-02',     '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000041', '韩医生', 'OUTPATIENT_DOCTOR', 'appointment:read,appointment:skip,medical-record:read,medical-record:write,medical-order:create,prescription:create', true, '00080002', now()),
    -- ── 内分泌科（门诊）──
    ('doctor-endo-01',     'doctor-endo-01',     '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000042', '沈医生', 'OUTPATIENT_DOCTOR', 'appointment:read,appointment:skip,medical-record:read,medical-record:write,medical-order:create,prescription:create', true, '00090001', now()),
    ('doctor-endo-02',     'doctor-endo-02',     '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000043', '秦医生', 'OUTPATIENT_DOCTOR', 'appointment:read,appointment:skip,medical-record:read,medical-record:write,medical-order:create,prescription:create', true, '00090002', now()),
    -- ── 呼吸内科（门诊）──
    ('doctor-resp-01',     'doctor-resp-01',     '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000047', '黄医生', 'OUTPATIENT_DOCTOR', 'appointment:read,appointment:skip,medical-record:read,medical-record:write,medical-order:create,prescription:create', true, '00100001', now()),
    ('doctor-resp-02',     'doctor-resp-02',     '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000048', '杨医生', 'OUTPATIENT_DOCTOR', 'appointment:read,appointment:skip,medical-record:read,medical-record:write,medical-order:create,prescription:create', true, '00100002', now()),
    -- ── 胸外科（门诊）──
    ('doctor-thoracic-01', 'doctor-thoracic-01', '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000049', '罗医生', 'OUTPATIENT_DOCTOR', 'appointment:read,appointment:skip,medical-record:read,medical-record:write,medical-order:create,prescription:create', true, '00110001', now()),
    ('doctor-thoracic-02', 'doctor-thoracic-02', '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000050', '许医生', 'OUTPATIENT_DOCTOR', 'appointment:read,appointment:skip,medical-record:read,medical-record:write,medical-order:create,prescription:create', true, '00110002', now()),
    -- ── 检验科（LAB_DOCTOR）──
    ('doctor-lab-01',      'doctor-lab-01',      '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000060', '方技师', 'LAB_DOCTOR', 'medical-order:read,medical-order:execute,report:confirm', true, '00120001', now()),
    ('doctor-lab-02',      'doctor-lab-02',      '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000061', '谢技师', 'LAB_DOCTOR', 'medical-order:read,medical-order:execute,report:confirm', true, '00120002', now()),
    ('doctor-lab-03',      'doctor-lab-03',      '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000062', '曹技师', 'LAB_DOCTOR', 'medical-order:read,medical-order:execute,report:confirm', true, '00120003', now()),
    -- ── 药房（PHARMACY_DOCTOR）──
    ('doctor-pharm-01',    'doctor-pharm-01',    '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000070', '宋药师', 'PHARMACY_DOCTOR', 'prescription:read,dispense:create,refund:create,inventory:manage', true, '00130001', now()),
    ('doctor-pharm-02',    'doctor-pharm-02',    '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000071', '唐药师', 'PHARMACY_DOCTOR', 'prescription:read,dispense:create,refund:create,inventory:manage', true, '00130002', now()),
    -- ── 处置室（DISPOSAL_DOCTOR）──
    ('doctor-disp-01',     'doctor-disp-01',     '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000080', '邓护士', 'DISPOSAL_DOCTOR', 'medical-order:read,medical-order:execute', true, '00140001', now()),
    ('doctor-disp-02',     'doctor-disp-02',     '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000081', '傅护士', 'DISPOSAL_DOCTOR', 'medical-order:read,medical-order:execute', true, '00140002', now()),
    -- ── 收费处（CASHIER）──
    ('cashier-01',         'cashier-01',         '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000090', '钱收费', 'CASHIER', 'appointment:create-offline,appointment:cancel,payment:create,refund:create', true, '00150001', now()),
    ('cashier-02',         'cashier-02',         '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000091', '孟收费', 'CASHIER', 'appointment:create-offline,appointment:cancel,payment:create,refund:create', true, '00150002', now())
on conflict (username) do update
    set password           = excluded.password,
        phone              = excluded.phone,
        name               = excluded.name,
        role               = excluded.role,
        permissions        = excluded.permissions,
        real_name_verified = excluded.real_name_verified,
        employee_no        = excluded.employee_no;

-- ────────────────────────────────────────────────────────────────
-- 3. 医生档案 (doctor.doctor)  ← 所有医护，含非门诊
-- ────────────────────────────────────────────────────────────────
insert into doctor.doctor (id, name, title, department_id, role_type, specialty, employee_no, active)
values
    -- 神经内科
    ('doctor-001',         '张医生', '主任医师',   'dept-neuro',     'OUTPATIENT_DOCTOR', '头痛、脑血管疾病及神经系统慢病', '00010001', true),
    ('doctor-neuro-02',    '王医生', '副主任医师', 'dept-neuro',     'OUTPATIENT_DOCTOR', '眩晕、睡眠障碍、神经康复随诊',   '00010002', true),
    ('doctor-neuro-03',    '刘医生', '主治医师',   'dept-neuro',     'OUTPATIENT_DOCTOR', '偏头痛、焦虑抑郁、慢病随访',     '00010003', true),
    -- 神经外科
    ('doctor-neurosurg-01','林医生', '主任医师',   'dept-neurosurg', 'OUTPATIENT_DOCTOR', '颅脑肿瘤切除、脑血管瘤介入',     '00020001', true),
    ('doctor-neurosurg-02','何医生', '主治医师',   'dept-neurosurg', 'OUTPATIENT_DOCTOR', '脑外伤随访、神经外科术后门诊',   '00020002', true),
    -- 检查科
    ('doctor-002',         '李医生', '副主任医师', 'dept-imaging',   'CHECK_DOCTOR',      '头部 CT/MRI 影像阅片',           '00030001', true),
    ('doctor-imaging-02',  '高医生', '主治医师',   'dept-imaging',   'CHECK_DOCTOR',      'B超、X线影像检查',               '00030002', true),
    ('doctor-imaging-03',  '马医生', '医师',       'dept-imaging',   'CHECK_DOCTOR',      '超声检查、心电图',               '00030003', true),
    -- 全科医学
    ('doctor-003',         '陈医生', '主治医师',   'dept-general',   'OUTPATIENT_DOCTOR', '常见病初诊、慢病复诊',           '00040001', true),
    ('doctor-general-02',  '赵医生', '副主任医师', 'dept-general',   'OUTPATIENT_DOCTOR', '高血压、糖尿病、健康咨询',       '00040002', true),
    ('doctor-general-03',  '孙医生', '主治医师',   'dept-general',   'OUTPATIENT_DOCTOR', '呼吸道感染、慢病随访',           '00040003', true),
    -- 康复科
    ('doctor-rehab-01',    '周医生', '主治医师',   'dept-rehab',     'OUTPATIENT_DOCTOR', '针灸、理疗、换药、术后康复',     '00050001', true),
    -- 心血管内科
    ('doctor-cardio-01',   '吴医生', '主任医师',   'dept-cardio',    'OUTPATIENT_DOCTOR', '冠心病、心律失常、心衰',         '00060001', true),
    ('doctor-cardio-02',   '郑医生', '主治医师',   'dept-cardio',    'OUTPATIENT_DOCTOR', '高血压、动脉硬化慢病随访',       '00060002', true),
    -- 骨科
    ('doctor-ortho-01',    '冯医生', '副主任医师', 'dept-ortho',     'OUTPATIENT_DOCTOR', '关节置换、脊柱疾病、骨质疏松',   '00070001', true),
    ('doctor-ortho-02',    '魏医生', '主治医师',   'dept-ortho',     'OUTPATIENT_DOCTOR', '骨折复位、运动损伤',             '00070002', true),
    -- 妇科
    ('doctor-gyne-01',     '蒋医生', '副主任医师', 'dept-gyne',      'OUTPATIENT_DOCTOR', '月经不调、宫颈疾病、孕期随诊',   '00080001', true),
    ('doctor-gyne-02',     '韩医生', '主治医师',   'dept-gyne',      'OUTPATIENT_DOCTOR', '妇科炎症、更年期管理',           '00080002', true),
    -- 内分泌科
    ('doctor-endo-01',     '沈医生', '主任医师',   'dept-endo',      'OUTPATIENT_DOCTOR', '糖尿病、甲状腺疾病、代谢综合征', '00090001', true),
    ('doctor-endo-02',     '秦医生', '主治医师',   'dept-endo',      'OUTPATIENT_DOCTOR', '糖尿病随访、肥胖管理、痛风',     '00090002', true),
    -- 呼吸内科
    ('doctor-resp-01',     '黄医生', '副主任医师', 'dept-resp',      'OUTPATIENT_DOCTOR', '慢阻肺、肺癌随诊、哮喘',         '00100001', true),
    ('doctor-resp-02',     '杨医生', '主治医师',   'dept-resp',      'OUTPATIENT_DOCTOR', '肺炎、肺结节随诊、慢病随访',     '00100002', true),
    -- 胸外科
    ('doctor-thoracic-01', '罗医生', '副主任医师', 'dept-thoracic',  'OUTPATIENT_DOCTOR', '肺叶切除、胸腔积液引流',         '00110001', true),
    ('doctor-thoracic-02', '许医生', '主治医师',   'dept-thoracic',  'OUTPATIENT_DOCTOR', '肺结节复查、气胸随访',           '00110002', true),
    -- 检验科
    ('doctor-lab-01',      '方技师', '主管技师',   'dept-lab',       'LAB_DOCTOR',        '血常规、生化全套',               '00120001', true),
    ('doctor-lab-02',      '谢技师', '技师',       'dept-lab',       'LAB_DOCTOR',        '微生物培养、免疫检验',           '00120002', true),
    ('doctor-lab-03',      '曹技师', '技师',       'dept-lab',       'LAB_DOCTOR',        '尿常规、凝血功能',               '00120003', true),
    -- 药房
    ('doctor-pharm-01',    '宋药师', '主管药师',   'dept-pharmacy',  'PHARMACY_DOCTOR',   '处方审核、用药咨询',             '00130001', true),
    ('doctor-pharm-02',    '唐药师', '药师',       'dept-pharmacy',  'PHARMACY_DOCTOR',   '门诊取药、药品调配',             '00130002', true),
    -- 处置室
    ('doctor-disp-01',     '邓护士', '主管护师',   'dept-disposal',  'DISPOSAL_DOCTOR',   '换药、注射、静脉输液',           '00140001', true),
    ('doctor-disp-02',     '傅护士', '护师',       'dept-disposal',  'DISPOSAL_DOCTOR',   '处置操作、护理记录',             '00140002', true),
    -- 收费处
    ('cashier-01',         '钱收费', null,          'dept-cashier',   'CASHIER',           null,                             '00150001', true),
    ('cashier-02',         '孟收费', null,          'dept-cashier',   'CASHIER',           null,                             '00150002', true)
on conflict (id) do update
    set name          = excluded.name,
        title         = excluded.title,
        department_id = excluded.department_id,
        role_type     = excluded.role_type,
        specialty     = excluded.specialty,
        employee_no   = excluded.employee_no,
        active        = excluded.active;

-- ────────────────────────────────────────────────────────────────
-- 4. 补全诊室 + 门诊医生扩展
-- V8 migration 仅为 migration 时存在的 3 个科室创建诊室，
-- seed 新增科室需在此补充，否则 outpatient_doctor 插入会被 exists 过滤掉。
-- ────────────────────────────────────────────────────────────────
insert into doctor.outpatient_clinic_room (id, department_id, name, location)
select 'room-' || id, id, name || '1号诊室', '门诊楼'
from doctor.department
on conflict (id) do update
    set name     = excluded.name,
        location = excluded.location;

insert into doctor.outpatient_doctor (doctor_id, clinic_room_id)
select d.id, 'room-' || d.department_id
from doctor.doctor d
where d.role_type = 'OUTPATIENT_DOCTOR'
  and exists (select 1 from doctor.outpatient_clinic_room r where r.id = 'room-' || d.department_id)
on conflict (doctor_id) do nothing;

-- ────────────────────────────────────────────────────────────────
-- 5. 排班 (doctor.doctor_schedule)  ← 仅门诊医生
-- ────────────────────────────────────────────────────────────────
insert into doctor.doctor_schedule (id, doctor_id, department_id, work_date, period, capacity, status, suspension_reason, updated_at)
values
    -- 神经内科 张医生
    ('sch-d001-001', 'doctor-001', 'dept-neuro', current_date + 1,  '上午', 12, 'PUBLISHED', null, now()),
    ('sch-d001-002', 'doctor-001', 'dept-neuro', current_date + 4,  '上午', 12, 'PUBLISHED', null, now()),
    ('sch-d001-003', 'doctor-001', 'dept-neuro', current_date + 8,  '上午', 12, 'PUBLISHED', null, now()),
    ('sch-d001-004', 'doctor-001', 'dept-neuro', current_date + 11, '上午', 12, 'PUBLISHED', null, now()),
    -- 神经内科 王医生
    ('sch-n02-001', 'doctor-neuro-02', 'dept-neuro', current_date + 2,  '上午', 14, 'PUBLISHED', null, now()),
    ('sch-n02-002', 'doctor-neuro-02', 'dept-neuro', current_date + 2,  '下午', 14, 'PUBLISHED', null, now()),
    ('sch-n02-003', 'doctor-neuro-02', 'dept-neuro', current_date + 5,  '上午', 14, 'PUBLISHED', null, now()),
    ('sch-n02-004', 'doctor-neuro-02', 'dept-neuro', current_date + 9,  '上午', 14, 'PUBLISHED', null, now()),
    ('sch-n02-005', 'doctor-neuro-02', 'dept-neuro', current_date + 9,  '下午', 14, 'PUBLISHED', null, now()),
    -- 神经内科 刘医生
    ('sch-n03-001', 'doctor-neuro-03', 'dept-neuro', current_date + 1,  '上午', 20, 'PUBLISHED', null, now()),
    ('sch-n03-002', 'doctor-neuro-03', 'dept-neuro', current_date + 1,  '下午', 20, 'PUBLISHED', null, now()),
    ('sch-n03-003', 'doctor-neuro-03', 'dept-neuro', current_date + 3,  '上午', 20, 'PUBLISHED', null, now()),
    ('sch-n03-004', 'doctor-neuro-03', 'dept-neuro', current_date + 5,  '上午', 20, 'PUBLISHED', null, now()),
    -- 全科 陈医生
    ('sch-d003-001', 'doctor-003', 'dept-general', current_date + 1, '全天', 30, 'PUBLISHED', null, now()),
    ('sch-d003-002', 'doctor-003', 'dept-general', current_date + 2, '全天', 30, 'PUBLISHED', null, now()),
    ('sch-d003-003', 'doctor-003', 'dept-general', current_date + 3, '全天', 30, 'PUBLISHED', null, now()),
    ('sch-d003-004', 'doctor-003', 'dept-general', current_date + 4, '全天', 30, 'PUBLISHED', null, now()),
    ('sch-d003-005', 'doctor-003', 'dept-general', current_date + 5, '全天', 30, 'PUBLISHED', null, now()),
    -- 心血管内科 吴医生
    ('sch-c01-001', 'doctor-cardio-01', 'dept-cardio', current_date + 2, '上午', 10, 'PUBLISHED', null, now()),
    ('sch-c01-002', 'doctor-cardio-01', 'dept-cardio', current_date + 5, '上午', 10, 'PUBLISHED', null, now()),
    ('sch-c01-003', 'doctor-cardio-01', 'dept-cardio', current_date + 9, '上午', 10, 'PUBLISHED', null, now()),
    -- 心血管内科 郑医生
    ('sch-c02-001', 'doctor-cardio-02', 'dept-cardio', current_date + 1, '上午', 20, 'PUBLISHED', null, now()),
    ('sch-c02-002', 'doctor-cardio-02', 'dept-cardio', current_date + 3, '上午', 20, 'PUBLISHED', null, now()),
    ('sch-c02-003', 'doctor-cardio-02', 'dept-cardio', current_date + 3, '下午', 18, 'PUBLISHED', null, now()),
    -- 内分泌科 沈医生
    ('sch-e01-001', 'doctor-endo-01', 'dept-endo', current_date + 3,  '上午', 10, 'PUBLISHED', null, now()),
    ('sch-e01-002', 'doctor-endo-01', 'dept-endo', current_date + 6,  '上午', 10, 'PUBLISHED', null, now()),
    ('sch-e01-003', 'doctor-endo-01', 'dept-endo', current_date + 10, '上午', 10, 'PUBLISHED', null, now()),
    -- 内分泌科 秦医生
    ('sch-e02-001', 'doctor-endo-02', 'dept-endo', current_date + 1, '上午', 22, 'PUBLISHED', null, now()),
    ('sch-e02-002', 'doctor-endo-02', 'dept-endo', current_date + 2, '上午', 22, 'PUBLISHED', null, now()),
    ('sch-e02-003', 'doctor-endo-02', 'dept-endo', current_date + 2, '下午', 20, 'PUBLISHED', null, now())
on conflict (id) do update
    set doctor_id         = excluded.doctor_id,
        department_id     = excluded.department_id,
        work_date         = excluded.work_date,
        period            = excluded.period,
        capacity          = excluded.capacity,
        status            = excluded.status,
        suspension_reason = excluded.suspension_reason,
        updated_at        = now();

commit;
