-- ══════════════════════════════════════════════════════════════════
-- 科室（code = 4位科室编码，即工号前4位）
-- ══════════════════════════════════════════════════════════════════
insert into department (id, name, code, description) values
  -- 门诊科室
  ('dept-neuro',      '神经内科',   '0001', '头痛、眩晕、脑血管疾病、癫痫、帕金森'),
  ('dept-general',    '全科医学',   '0002', '常见病与慢病复诊、健康管理'),
  ('dept-ortho',      '骨科',       '0003', '骨折、关节置换、脊柱外科'),
  ('dept-cardio',     '心内科',     '0004', '冠心病、心力衰竭、心律失常'),
  ('dept-gastro',     '消化内科',   '0005', '胃肠疾病、肝胆胰疾病'),
  ('dept-pulmo',      '呼吸内科',   '0006', '肺炎、哮喘、慢阻肺、肺结节'),
  ('dept-endo',       '内分泌科',   '0007', '糖尿病、甲状腺、代谢综合征'),
  ('dept-neuro-surg', '神经外科',   '0008', '脑肿瘤、颅脑外伤、脑出血手术'),
  -- 医技科室
  ('dept-imaging',    '影像检查科', '0100', 'CT、MRI、X光、超声等影像检查'),
  ('dept-lab',        '检验科',     '0200', '血液、尿液、生化、免疫等检验'),
  ('dept-disposal',   '处置科',     '0300', '静脉输液、换药、注射、理疗'),
  -- 支持科室
  ('dept-pharmacy',   '药房',       '0400', '药品调配、发放、用药指导'),
  ('dept-cashier',    '收费处',     '0500', '挂号收费、退费、医保结算'),
  ('dept-admin',      '系统管理',   '0900', '平台管理员')
on conflict (id) do nothing;

-- ══════════════════════════════════════════════════════════════════
-- 员工档案（id = employee_no = auth.user_account.id）
-- ══════════════════════════════════════════════════════════════════
insert into staff (id, name, employee_no, department_id, role_type, title, specialty) values
  -- 神经内科
  ('00010001','张医生','00010001','dept-neuro',     'OUTPATIENT_DOCTOR','主任医师',  '头痛与脑血管疾病、帕金森病'),
  ('00010002','刘医生','00010002','dept-neuro',     'OUTPATIENT_DOCTOR','副主任医师','癫痫、头晕、神经变性病'),
  ('00010003','王医生','00010003','dept-neuro',     'OUTPATIENT_DOCTOR','主治医师',  '脑梗、TIA、神经痛'),
  -- 全科医学
  ('00020001','陈医生','00020001','dept-general',   'OUTPATIENT_DOCTOR','主治医师',  '慢病管理、高血压、糖尿病复诊'),
  ('00020002','黄医生','00020002','dept-general',   'OUTPATIENT_DOCTOR','主治医师',  '常见感染、健康体检解读'),
  -- 骨科
  ('00030001','孙医生','00030001','dept-ortho',     'OUTPATIENT_DOCTOR','主任医师',  '脊柱外科、颈腰椎病'),
  ('00030002','林医生','00030002','dept-ortho',     'OUTPATIENT_DOCTOR','副主任医师','骨折复位、关节外科'),
  -- 心内科
  ('00040001','吴医生','00040001','dept-cardio',    'OUTPATIENT_DOCTOR','主任医师',  '冠心病、心力衰竭、起搏器随访'),
  ('00040002','郑医生','00040002','dept-cardio',    'OUTPATIENT_DOCTOR','主治医师',  '高血压、心律失常'),
  -- 消化内科
  ('00050001','冯医生','00050001','dept-gastro',    'OUTPATIENT_DOCTOR','主治医师',  '胃炎、消化道溃疡、炎症性肠病'),
  ('00050002','蒋医生','00050002','dept-gastro',    'OUTPATIENT_DOCTOR','主治医师',  '肝病、胆胰疾病'),
  -- 呼吸内科
  ('00060001','韩医生','00060001','dept-pulmo',     'OUTPATIENT_DOCTOR','副主任医师','慢阻肺、哮喘、肺结节随访'),
  ('00060002','杨医生','00060002','dept-pulmo',     'OUTPATIENT_DOCTOR','主治医师',  '肺炎、呼吸衰竭'),
  -- 内分泌科
  ('00070001','朱医生','00070001','dept-endo',      'OUTPATIENT_DOCTOR','主任医师',  '糖尿病、甲亢、骨质疏松'),
  ('00070002','许医生','00070002','dept-endo',      'OUTPATIENT_DOCTOR','主治医师',  '肥胖症、高脂血症'),
  -- 神经外科
  ('00080001','曹医生','00080001','dept-neuro-surg','OUTPATIENT_DOCTOR','主任医师',  '脑肿瘤、脑出血手术'),
  ('00080002','魏医生','00080002','dept-neuro-surg','OUTPATIENT_DOCTOR','副主任医师','颅脑外伤、蛛网膜下腔出血'),
  -- 影像检查科（CHECK_DOCTOR）
  ('01000001','李影医','01000001','dept-imaging',   'CHECK_DOCTOR',     '副主任技师','CT/MRI综合阅片'),
  ('01000002','吴影医','01000002','dept-imaging',   'CHECK_DOCTOR',     '主任技师',  'CT/MRI综合阅片'),
  ('01000003','马影医','01000003','dept-imaging',   'CHECK_DOCTOR',     '主治技师',  'CT专科影像'),
  ('01000004','胡影医','01000004','dept-imaging',   'CHECK_DOCTOR',     '主治技师',  'MRI专科影像'),
  ('01000005','杜影医','01000005','dept-imaging',   'CHECK_DOCTOR',     '技师',      'X光/超声'),
  -- 检验科（LAB_DOCTOR）
  ('02000001','王技师','02000001','dept-lab',       'LAB_DOCTOR',       '主管技师',  '血常规、生化、凝血'),
  ('02000002','钱技师','02000002','dept-lab',       'LAB_DOCTOR',       '主管技师',  '血常规、生化'),
  ('02000003','沈技师','02000003','dept-lab',       'LAB_DOCTOR',       '技师',      '急诊检验'),
  ('02000004','秦技师','02000004','dept-lab',       'LAB_DOCTOR',       '技师',      '生化免疫'),
  ('02000005','尤技师','02000005','dept-lab',       'LAB_DOCTOR',       '技师',      '免疫检验'),
  -- 处置科（DISPOSAL_DOCTOR）
  ('03000001','赵护师','03000001','dept-disposal',  'DISPOSAL_DOCTOR',  '主管护师',  '静脉输液、换药处置'),
  ('03000002','周护师','03000002','dept-disposal',  'DISPOSAL_DOCTOR',  '主管护师',  '静脉输液、注射'),
  ('03000003','徐护师','03000003','dept-disposal',  'DISPOSAL_DOCTOR',  '护师',      '急诊输液处置'),
  ('03000004','许护师','03000004','dept-disposal',  'DISPOSAL_DOCTOR',  '护师',      '康复理疗'),
  ('03000005','何护师','03000005','dept-disposal',  'DISPOSAL_DOCTOR',  '护士',      '注射室'),
  -- 药房
  ('04000001','林药师','04000001','dept-pharmacy',  'PHARMACY_STAFF',   '主管药师',  '处方调配、用药指导'),
  ('04000002','吕药师','04000002','dept-pharmacy',  'PHARMACY_STAFF',   '药师',      '药品调配'),
  -- 收费处
  ('05000001','收费员甲','05000001','dept-cashier', 'CASHIER',          null,        null),
  ('05000002','收费员乙','05000002','dept-cashier', 'CASHIER',          null,        null),
  -- 系统管理
  ('09000001','系统管理员','09000001','dept-admin', 'ADMIN',            null,        null)
on conflict (id) do nothing;

-- ══════════════════════════════════════════════════════════════════
-- 门诊诊室
-- ══════════════════════════════════════════════════════════════════
insert into outpatient_room (id, department_id, name, location) values
  ('room-neuro-01',    'dept-neuro',     '神经内科1号诊室', '门诊楼3层301'),
  ('room-neuro-02',    'dept-neuro',     '神经内科2号诊室', '门诊楼3层302'),
  ('room-neuro-03',    'dept-neuro',     '神经内科3号诊室', '门诊楼3层303'),
  ('room-general-01',  'dept-general',   '全科医学1号诊室', '门诊楼2层201'),
  ('room-general-02',  'dept-general',   '全科医学2号诊室', '门诊楼2层202'),
  ('room-ortho-01',    'dept-ortho',     '骨科1号诊室',     '门诊楼4层401'),
  ('room-ortho-02',    'dept-ortho',     '骨科2号诊室',     '门诊楼4层402'),
  ('room-cardio-01',   'dept-cardio',    '心内科1号诊室',   '门诊楼4层411'),
  ('room-cardio-02',   'dept-cardio',    '心内科2号诊室',   '门诊楼4层412'),
  ('room-gastro-01',   'dept-gastro',    '消化内科1号诊室', '门诊楼5层501'),
  ('room-gastro-02',   'dept-gastro',    '消化内科2号诊室', '门诊楼5层502'),
  ('room-pulmo-01',    'dept-pulmo',     '呼吸内科1号诊室', '门诊楼5层511'),
  ('room-pulmo-02',    'dept-pulmo',     '呼吸内科2号诊室', '门诊楼5层512'),
  ('room-endo-01',     'dept-endo',      '内分泌科1号诊室', '门诊楼6层601'),
  ('room-endo-02',     'dept-endo',      '内分泌科2号诊室', '门诊楼6层602'),
  ('room-neuro-surg-01','dept-neuro-surg','神经外科1号诊室','门诊楼6层611'),
  ('room-neuro-surg-02','dept-neuro-surg','神经外科2号诊室','门诊楼6层612')
on conflict (department_id, name) do nothing;

insert into outpatient_doctor (staff_id, room_id) values
  ('00010001','room-neuro-01'),('00010002','room-neuro-02'),('00010003','room-neuro-03'),
  ('00020001','room-general-01'),('00020002','room-general-02'),
  ('00030001','room-ortho-01'), ('00030002','room-ortho-02'),
  ('00040001','room-cardio-01'),('00040002','room-cardio-02'),
  ('00050001','room-gastro-01'),('00050002','room-gastro-02'),
  ('00060001','room-pulmo-01'), ('00060002','room-pulmo-02'),
  ('00070001','room-endo-01'),  ('00070002','room-endo-02'),
  ('00080001','room-neuro-surg-01'),('00080002','room-neuro-surg-02')
on conflict (staff_id) do nothing;

-- ══════════════════════════════════════════════════════════════════
-- 医疗项目目录（每类 ≥ 5 条）
-- ══════════════════════════════════════════════════════════════════
insert into medical_item (code, name, category, price) values
  -- CHECK（影像检查）
  ('CT-HEAD',       '头部CT平扫',           'CHECK',  260.00),
  ('CT-CHEST',      '胸部CT平扫',           'CHECK',  300.00),
  ('CT-ABDOMEN',    '腹部CT平扫',           'CHECK',  320.00),
  ('CT-LUMBAR',     '腰椎CT',               'CHECK',  280.00),
  ('MRI-BRAIN',     '颅脑MRI平扫',          'CHECK',  680.00),
  ('MRI-SPINE',     '全脊柱MRI',            'CHECK',  780.00),
  ('MRI-KNEE',      '膝关节MRI',            'CHECK',  680.00),
  ('XRAY-CHEST',    '胸部X光正侧位',        'CHECK',   80.00),
  ('XRAY-LUMBAR',   '腰椎X光',              'CHECK',   70.00),
  ('US-ABDOMEN',    '腹部超声',             'CHECK',  120.00),
  ('US-THYROID',    '甲状腺超声',           'CHECK',   80.00),
  ('US-CARDIAC',    '超声心动图',           'CHECK',  280.00),
  -- LAB（检验）
  ('CBC',           '血常规',               'LAB',     35.00),
  ('LIVER',         '肝功能全套',           'LAB',     75.00),
  ('KIDNEY',        '肾功能',               'LAB',     60.00),
  ('BLOOD-GLUCOSE', '空腹血糖',             'LAB',     15.00),
  ('HBA1C',         '糖化血红蛋白',         'LAB',     45.00),
  ('URINE-RT',      '尿常规',               'LAB',     18.00),
  ('LIPID',         '血脂四项',             'LAB',     65.00),
  ('COAGULATION',   '凝血功能四项',         'LAB',     85.00),
  ('THYROID',       '甲状腺功能五项',       'LAB',    120.00),
  ('CARDIAC-ENZ',   '心肌酶谱',             'LAB',     80.00),
  ('CRP',           'C反应蛋白',            'LAB',     25.00),
  ('ESR',           '血沉',                 'LAB',     20.00),
  ('INFECT-SCREEN', '乙肝两对半+丙肝抗体',  'LAB',     90.00),
  -- DISPOSAL（处置）
  ('DISP-INFUSION', '静脉输液处置',         'DISPOSAL', 25.00),
  ('DISP-DRESSING', '伤口换药',             'DISPOSAL', 20.00),
  ('DISP-INJECTION','肌肉注射',             'DISPOSAL', 10.00),
  ('DISP-NEBULIZER','雾化吸入治疗',         'DISPOSAL', 30.00),
  ('DISP-ECG',      '12导联心电图',         'DISPOSAL', 25.00),
  ('DISP-REHAB',    '神经康复理疗',         'DISPOSAL', 50.00),
  ('DISP-LUMBAR-PUNTURE','腰椎穿刺术',     'DISPOSAL',180.00),
  -- DRUG（药品）
  ('DRUG-ASPIRIN',  '阿司匹林肠溶片100mg', 'DRUG',    18.50),
  ('DRUG-ATORVAST', '阿托伐他汀钙片20mg',  'DRUG',    32.00),
  ('DRUG-MANNITOL', '甘露醇注射液250ml',   'DRUG',    12.00),
  ('DRUG-METOPROLOL','美托洛尔缓释片47.5mg','DRUG',   28.00),
  ('DRUG-AMLODIPINE','氨氯地平片5mg',      'DRUG',    15.00),
  ('DRUG-CLOPIDOGR','氯吡格雷片75mg',      'DRUG',    45.00),
  ('DRUG-WARFARIN', '华法林钠片3mg',        'DRUG',     8.00),
  ('DRUG-INSULIN',  '胰岛素注射液300U/3ml','DRUG',    35.00),
  ('DRUG-PANTOPRAZ','泮托拉唑肠溶胶囊40mg','DRUG',    22.00),
  ('DRUG-DEXAMETH', '地塞米松注射液5mg',   'DRUG',     3.50),
  ('DRUG-LEVETIRAC','左乙拉西坦片500mg',   'DRUG',    68.00),
  ('DRUG-LISINOPRIL','赖诺普利片10mg',     'DRUG',    14.00)
on conflict (code) do nothing;

-- ══════════════════════════════════════════════════════════════════
-- 今日排班 + 时间槽（DevQueueSeeder 每日刷新 visit_date）
-- ══════════════════════════════════════════════════════════════════
insert into schedule (id, staff_id, department_id, work_date, period, capacity) values
  ('sched-00010001-am','00010001','dept-neuro',    current_date,    '上午',20),
  ('sched-00010001-pm','00010001','dept-neuro',    current_date+1,  '下午',18),
  ('sched-00010002-am','00010002','dept-neuro',    current_date,    '上午',20),
  ('sched-00020001-ft','00020001','dept-general',  current_date,    '全天',30),
  ('sched-00020002-am','00020002','dept-general',  current_date,    '上午',20),
  ('sched-00030001-am','00030001','dept-ortho',    current_date,    '上午',18),
  ('sched-00040001-am','00040001','dept-cardio',   current_date,    '上午',20),
  ('sched-00050001-am','00050001','dept-gastro',   current_date,    '上午',18),
  ('sched-00060001-am','00060001','dept-pulmo',    current_date,    '上午',18),
  ('sched-00070001-am','00070001','dept-endo',     current_date,    '上午',20),
  ('sched-00080001-am','00080001','dept-neuro-surg',current_date,   '上午',15),
  ('sched-ct-valid',   '00010001','dept-neuro',    current_date,    '下午',30)
on conflict (staff_id, work_date, period) do nothing;

-- 张医生 上午时槽（每15分钟，08:00-11:30，共15个）
insert into schedule_slot (id, schedule_id, start_time, capacity) values
  ('slot-am-0800','sched-00010001-am','08:00',2),('slot-am-0815','sched-00010001-am','08:15',2),
  ('slot-am-0830','sched-00010001-am','08:30',2),('slot-am-0845','sched-00010001-am','08:45',2),
  ('slot-am-0900','sched-00010001-am','09:00',2),('slot-am-0915','sched-00010001-am','09:15',2),
  ('slot-am-0930','sched-00010001-am','09:30',1),('slot-am-0945','sched-00010001-am','09:45',1),
  ('slot-am-1000','sched-00010001-am','10:00',1),('slot-am-1015','sched-00010001-am','10:15',1),
  ('slot-am-1030','sched-00010001-am','10:30',1),('slot-am-1045','sched-00010001-am','10:45',1),
  ('slot-am-1100','sched-00010001-am','11:00',1),('slot-am-1115','sched-00010001-am','11:15',1),
  ('slot-am-1130','sched-00010001-am','11:30',1)
on conflict (schedule_id, start_time) do nothing;

-- CT验证队列 下午时槽（每10分钟，13:00-15:40，共15个）
insert into schedule_slot (id, schedule_id, start_time, capacity) values
  ('slot-ct-1300','sched-ct-valid','13:00',2),('slot-ct-1310','sched-ct-valid','13:10',2),
  ('slot-ct-1320','sched-ct-valid','13:20',2),('slot-ct-1330','sched-ct-valid','13:30',2),
  ('slot-ct-1340','sched-ct-valid','13:40',2),('slot-ct-1400','sched-ct-valid','14:00',2),
  ('slot-ct-1410','sched-ct-valid','14:10',2),('slot-ct-1420','sched-ct-valid','14:20',2),
  ('slot-ct-1430','sched-ct-valid','14:30',2),('slot-ct-1440','sched-ct-valid','14:40',2),
  ('slot-ct-1500','sched-ct-valid','15:00',2),('slot-ct-1510','sched-ct-valid','15:10',2),
  ('slot-ct-1520','sched-ct-valid','15:20',1),('slot-ct-1530','sched-ct-valid','15:30',1),
  ('slot-ct-1540','sched-ct-valid','15:40',1)
on conflict (schedule_id, start_time) do nothing;
