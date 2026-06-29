-- ══════════════════════════════════════════════════════════════════
-- 执行诊室（每类 ≥ 5 个，为 AI 分诊提供真实调度空间）
-- ══════════════════════════════════════════════════════════════════
insert into examination_room (id, name, room_type, location, equipment_ids, capacity) values
  -- CHECK 检查室（5个）
  ('rm-chk-01','综合影像室A',  'CHECK','医技楼2层201','CT-01,MRI-01',        20),
  ('rm-chk-02','综合影像室B',  'CHECK','医技楼2层202','CT-02,MRI-02',        20),
  ('rm-chk-03','CT专科室',     'CHECK','医技楼2层203','CT-03',               15),
  ('rm-chk-04','MRI专科室',    'CHECK','医技楼3层301','MRI-03',              10),
  ('rm-chk-05','X光超声室',    'CHECK','医技楼1层101','XRAY-01,US-01',       25),
  -- LAB 检验室（5个）
  ('rm-lab-01','综合检验室A',  'LAB',  '医技楼1层111','LAB-01',              40),
  ('rm-lab-02','综合检验室B',  'LAB',  '医技楼1层112','LAB-02',              35),
  ('rm-lab-03','急诊检验室',   'LAB',  '急诊楼2层201','LAB-03',              20),
  ('rm-lab-04','生化免疫室',   'LAB',  '医技楼1层113','LAB-04',              30),
  ('rm-lab-05','特殊检验室',   'LAB',  '医技楼1层114','LAB-05',              20),
  -- DISPOSAL 处置室（5个）
  ('rm-dsp-01','综合处置室A',  'DISPOSAL','门诊楼3层301',null,               30),
  ('rm-dsp-02','综合处置室B',  'DISPOSAL','门诊楼3层302',null,               25),
  ('rm-dsp-03','急诊处置室',   'DISPOSAL','急诊楼1层101',null,               15),
  ('rm-dsp-04','康复理疗室',   'DISPOSAL','康复楼1层101','REHAB-01,REHAB-02',20),
  ('rm-dsp-05','注射室',       'DISPOSAL','门诊楼1层107',null,               20)
on conflict (id) do nothing;

-- ══════════════════════════════════════════════════════════════════
-- 诊室项目能力（room_item_capability）
-- ══════════════════════════════════════════════════════════════════
insert into room_item_capability (room_id, item_code, item_name, priority) values
  -- rm-chk-01 综合影像室A：CT + MRI
  ('rm-chk-01','CT-HEAD',    '头部CT平扫',     10),
  ('rm-chk-01','CT-CHEST',   '胸部CT平扫',     10),
  ('rm-chk-01','CT-ABDOMEN', '腹部CT平扫',     10),
  ('rm-chk-01','CT-LUMBAR',  '腰椎CT',         10),
  ('rm-chk-01','MRI-BRAIN',  '颅脑MRI平扫',    20),
  ('rm-chk-01','MRI-SPINE',  '全脊柱MRI',      20),
  ('rm-chk-01','MRI-KNEE',   '膝关节MRI',      20),
  -- rm-chk-02 综合影像室B：CT + MRI
  ('rm-chk-02','CT-HEAD',    '头部CT平扫',     10),
  ('rm-chk-02','CT-CHEST',   '胸部CT平扫',     10),
  ('rm-chk-02','CT-ABDOMEN', '腹部CT平扫',     10),
  ('rm-chk-02','CT-LUMBAR',  '腰椎CT',         10),
  ('rm-chk-02','MRI-BRAIN',  '颅脑MRI平扫',    20),
  ('rm-chk-02','MRI-SPINE',  '全脊柱MRI',      20),
  ('rm-chk-02','MRI-KNEE',   '膝关节MRI',      20),
  -- rm-chk-03 CT专科室：仅CT
  ('rm-chk-03','CT-HEAD',    '头部CT平扫',     10),
  ('rm-chk-03','CT-CHEST',   '胸部CT平扫',     10),
  ('rm-chk-03','CT-ABDOMEN', '腹部CT平扫',     10),
  ('rm-chk-03','CT-LUMBAR',  '腰椎CT',         10),
  -- rm-chk-04 MRI专科室：仅MRI
  ('rm-chk-04','MRI-BRAIN',  '颅脑MRI平扫',    10),
  ('rm-chk-04','MRI-SPINE',  '全脊柱MRI',      10),
  ('rm-chk-04','MRI-KNEE',   '膝关节MRI',      10),
  -- rm-chk-05 X光超声室
  ('rm-chk-05','XRAY-CHEST', '胸部X光正侧位',  10),
  ('rm-chk-05','XRAY-LUMBAR','腰椎X光',        10),
  ('rm-chk-05','US-ABDOMEN', '腹部超声',       10),
  ('rm-chk-05','US-THYROID', '甲状腺超声',     10),
  ('rm-chk-05','US-CARDIAC', '超声心动图',     10),
  -- rm-lab-01 综合检验室A：全项目
  ('rm-lab-01','CBC',         '血常规',         10),
  ('rm-lab-01','LIVER',       '肝功能全套',     10),
  ('rm-lab-01','KIDNEY',      '肾功能',         10),
  ('rm-lab-01','BLOOD-GLUCOSE','空腹血糖',      10),
  ('rm-lab-01','HBA1C',       '糖化血红蛋白',   10),
  ('rm-lab-01','URINE-RT',    '尿常规',         10),
  ('rm-lab-01','LIPID',       '血脂四项',       10),
  ('rm-lab-01','COAGULATION', '凝血功能四项',   10),
  ('rm-lab-01','THYROID',     '甲状腺功能五项', 20),
  ('rm-lab-01','CRP',         'C反应蛋白',      10),
  ('rm-lab-01','ESR',         '血沉',           10),
  ('rm-lab-01','INFECT-SCREEN','乙肝丙肝筛查',  20),
  -- rm-lab-02 综合检验室B：常规项目
  ('rm-lab-02','CBC',         '血常规',         10),
  ('rm-lab-02','LIVER',       '肝功能全套',     10),
  ('rm-lab-02','KIDNEY',      '肾功能',         10),
  ('rm-lab-02','BLOOD-GLUCOSE','空腹血糖',      10),
  ('rm-lab-02','URINE-RT',    '尿常规',         10),
  ('rm-lab-02','LIPID',       '血脂四项',       10),
  ('rm-lab-02','COAGULATION', '凝血功能四项',   10),
  ('rm-lab-02','CRP',         'C反应蛋白',      10),
  -- rm-lab-03 急诊检验室：急检项目，快速出结果
  ('rm-lab-03','CBC',         '血常规（急检）', 10),
  ('rm-lab-03','COAGULATION', '凝血（急检）',   10),
  ('rm-lab-03','CARDIAC-ENZ', '心肌酶谱（急检）',10),
  ('rm-lab-03','BLOOD-GLUCOSE','血糖（急检）',  10),
  ('rm-lab-03','CRP',         'CRP（急检）',    10),
  -- rm-lab-04 生化免疫室
  ('rm-lab-04','LIVER',       '肝功能全套',     10),
  ('rm-lab-04','KIDNEY',      '肾功能',         10),
  ('rm-lab-04','LIPID',       '血脂四项',       10),
  ('rm-lab-04','THYROID',     '甲状腺功能五项', 10),
  ('rm-lab-04','HBA1C',       '糖化血红蛋白',   10),
  ('rm-lab-04','CARDIAC-ENZ', '心肌酶谱',       10),
  ('rm-lab-04','INFECT-SCREEN','乙肝丙肝筛查',  10),
  -- rm-lab-05 特殊检验室
  ('rm-lab-05','THYROID',     '甲状腺功能五项', 10),
  ('rm-lab-05','ESR',         '血沉',           10),
  ('rm-lab-05','INFECT-SCREEN','乙肝丙肝筛查',  10),
  -- rm-dsp-01 综合处置室A
  ('rm-dsp-01','DISP-INFUSION','静脉输液处置',  10),
  ('rm-dsp-01','DISP-DRESSING','伤口换药',      10),
  ('rm-dsp-01','DISP-INJECTION','肌肉注射',     10),
  ('rm-dsp-01','DISP-NEBULIZER','雾化吸入',     10),
  ('rm-dsp-01','DISP-ECG',    '12导联心电图',   10),
  -- rm-dsp-02 综合处置室B
  ('rm-dsp-02','DISP-INFUSION','静脉输液处置',  10),
  ('rm-dsp-02','DISP-DRESSING','伤口换药',      10),
  ('rm-dsp-02','DISP-INJECTION','肌肉注射',     10),
  ('rm-dsp-02','DISP-NEBULIZER','雾化吸入',     10),
  ('rm-dsp-02','DISP-ECG',    '12导联心电图',   10),
  -- rm-dsp-03 急诊处置室：输液为主
  ('rm-dsp-03','DISP-INFUSION','急诊输液',      10),
  ('rm-dsp-03','DISP-INJECTION','急诊注射',     10),
  ('rm-dsp-03','DISP-ECG',    '急诊心电图',     10),
  -- rm-dsp-04 康复理疗室
  ('rm-dsp-04','DISP-REHAB',  '神经康复理疗',   10),
  ('rm-dsp-04','DISP-NEBULIZER','雾化吸入',     20),
  -- rm-dsp-05 注射室
  ('rm-dsp-05','DISP-INJECTION','肌肉注射',     10),
  ('rm-dsp-05','DISP-INFUSION','静脉输液',      20)
on conflict (room_id, item_code) do nothing;

-- ══════════════════════════════════════════════════════════════════
-- 员工-诊室绑定（staff_id = doctor.staff.id = 工号）
-- ══════════════════════════════════════════════════════════════════
insert into staff_room_assignment (staff_id, room_id) values
  -- 影像检查科
  ('01000001','rm-chk-01'),
  ('01000002','rm-chk-02'),
  ('01000003','rm-chk-03'),
  ('01000004','rm-chk-04'),
  ('01000005','rm-chk-05'),
  -- 检验科
  ('02000001','rm-lab-01'),
  ('02000002','rm-lab-02'),
  ('02000003','rm-lab-03'),
  ('02000004','rm-lab-04'),
  ('02000005','rm-lab-05'),
  -- 处置科
  ('03000001','rm-dsp-01'),
  ('03000002','rm-dsp-02'),
  ('03000003','rm-dsp-03'),
  ('03000004','rm-dsp-04'),
  ('03000005','rm-dsp-05')
on conflict (staff_id, room_id) do nothing;
