-- ══════════════════════════════════════════════════════════════════
-- seed-drugs-and-today-queue.sql
-- 补充药品目录 + 张医生（doctor-001）今日候诊队列
-- 执行方式：psql -h <host> -U <user> -d <db> -f seed-drugs-and-today-queue.sql
-- ══════════════════════════════════════════════════════════════════
begin;

-- ──────────────────────────────────────────────────────────────────
-- 1. 药品目录（pharmacy schema）
-- ──────────────────────────────────────────────────────────────────
insert into pharmacy.drug_catalog (id, drug_code, drug_name, specification, unit, unit_price, enabled)
values
  -- 神经系统
  ('drug-ibuprofen',        'DRUG-010', '布洛芬缓释胶囊',       '0.3g×12粒',       '盒',   8.50,  true),
  ('drug-acetaminophen',    'DRUG-011', '对乙酰氨基酚片',       '500mg×20片',      '盒',   5.80,  true),
  ('drug-flunarizine',      'DRUG-012', '氟桂利嗪胶囊',         '5mg×20粒',        '盒',  12.00,  true),
  ('drug-nimodipine',       'DRUG-013', '尼莫地平片',           '30mg×30片',       '盒',  28.50,  true),
  ('drug-betahistine',      'DRUG-014', '甲磺酸倍他司汀片',     '6mg×30片',        '盒',  15.00,  true),
  ('drug-diazepam',         'DRUG-015', '地西泮片',             '2.5mg×20片',      '盒',   6.00,  true),
  ('drug-carbamazepine',    'DRUG-016', '卡马西平片',           '200mg×30片',      '盒',  18.00,  true),
  -- 心血管
  ('drug-amlodipine',       'DRUG-020', '苯磺酸氨氯地平片',     '5mg×7片',         '盒',  22.00,  true),
  ('drug-metoprolol',       'DRUG-021', '酒石酸美托洛尔片',     '25mg×20片',       '盒',  12.80,  true),
  ('drug-aspirin',          'DRUG-002', '阿司匹林肠溶片',       '100mg×30片',      '盒',  18.50,  true),
  ('drug-atorvastatin',     'DRUG-003', '阿托伐他汀钙片',       '20mg×7片',        '盒',  29.00,  true),
  ('drug-clopidogrel',      'DRUG-022', '硫酸氢氯吡格雷片',     '75mg×7片',        '盒',  48.00,  true),
  ('drug-isosorbide',       'DRUG-023', '单硝酸异山梨酯缓释片', '40mg×30片',       '盒',  32.00,  true),
  -- 消化系统
  ('drug-omeprazole',       'DRUG-030', '奥美拉唑肠溶胶囊',     '20mg×14粒',       '盒',  22.00,  true),
  ('drug-mosapride',        'DRUG-031', '枸橼酸莫沙必利片',     '5mg×12片',        '盒',  14.50,  true),
  ('drug-domperidone',      'DRUG-032', '多潘立酮片',           '10mg×30片',       '盒',   9.80,  true),
  -- 抗感染
  ('drug-amoxicillin',      'DRUG-040', '阿莫西林胶囊',         '0.5g×24粒',       '盒',  12.00,  true),
  ('drug-azithromycin',     'DRUG-041', '阿奇霉素片',           '0.25g×6片',       '盒',  28.00,  true),
  ('drug-levofloxacin',     'DRUG-042', '盐酸左氧氟沙星片',     '0.5g×10片',       '盒',  24.00,  true),
  ('drug-cefuroxime',       'DRUG-043', '头孢呋辛酯片',         '0.25g×10片',      '盒',  36.00,  true),
  -- 内分泌/代谢
  ('drug-metformin',        'DRUG-050', '盐酸二甲双胍缓释片',   '0.5g×30片',       '盒',  18.00,  true),
  ('drug-glimepiride',      'DRUG-051', '格列美脲片',           '2mg×15片',        '盒',  25.00,  true),
  ('drug-insulin-glargine', 'DRUG-052', '甘精胰岛素注射液',     '300U/3ml×1支',    '支', 168.00,  true),
  ('drug-levothyroxine',    'DRUG-053', '左甲状腺素钠片',       '50μg×100片',      '瓶',  42.00,  true),
  -- 呼吸系统
  ('drug-salbutamol',       'DRUG-060', '硫酸沙丁胺醇气雾剂',   '100μg×200揿',     '支',  38.00,  true),
  ('drug-budesonide',       'DRUG-061', '布地奈德鼻喷雾剂',     '64μg/喷×120喷',   '支',  48.00,  true),
  ('drug-acetylcysteine',   'DRUG-062', '乙酰半胱氨酸颗粒',     '0.1g×10袋',       '盒',  18.00,  true),
  -- 输液/注射
  ('drug-mannitol',         'DRUG-001', '甘露醇注射液',         '250ml:50g',       '瓶',  12.80,  true),
  ('drug-saline-250',       'DRUG-070', '氯化钠注射液',         '250ml:2.25g',     '袋',   3.50,  true),
  ('drug-glucose-250',      'DRUG-071', '葡萄糖注射液',         '250ml:12.5g',     '袋',   3.50,  true),
  -- 维生素/辅助
  ('drug-vit-b1',           'DRUG-080', '维生素B₁片',           '10mg×100片',      '瓶',   4.50,  true),
  ('drug-vit-b12',          'DRUG-081', '甲钴胺片',             '0.5mg×20片',      '盒',  28.60,  true),
  ('drug-vit-c',            'DRUG-082', '维生素C片',            '0.1g×100片',      '瓶',   5.00,  true),
  ('drug-calcium-d3',       'DRUG-083', '碳酸钙D₃片',           '600mg×60片',      '盒',  22.00,  true)
on conflict (drug_code) do update
  set drug_name     = excluded.drug_name,
      specification = excluded.specification,
      unit          = excluded.unit,
      unit_price    = excluded.unit_price,
      enabled       = excluded.enabled;

-- 初始化库存（若已存在则不覆盖）
insert into pharmacy.drug_inventory (drug_id, quantity, warning_threshold)
select id, 300, 30
from pharmacy.drug_catalog
where id in (
  'drug-ibuprofen','drug-acetaminophen','drug-flunarizine','drug-nimodipine',
  'drug-betahistine','drug-diazepam','drug-carbamazepine',
  'drug-amlodipine','drug-metoprolol','drug-aspirin','drug-atorvastatin',
  'drug-clopidogrel','drug-isosorbide',
  'drug-omeprazole','drug-mosapride','drug-domperidone',
  'drug-amoxicillin','drug-azithromycin','drug-levofloxacin','drug-cefuroxime',
  'drug-metformin','drug-glimepiride','drug-insulin-glargine','drug-levothyroxine',
  'drug-salbutamol','drug-budesonide','drug-acetylcysteine',
  'drug-mannitol','drug-saline-250','drug-glucose-250',
  'drug-vit-b1','drug-vit-b12','drug-vit-c','drug-calcium-d3'
)
on conflict (drug_id) do nothing;

-- ──────────────────────────────────────────────────────────────────
-- 2. 今日号源（appointment schema）
--    slot_inventory 只需要一条记录，实际号源由直接插入的 appointment 体现
-- ──────────────────────────────────────────────────────────────────
insert into appointment.slot_inventory (schedule_id, capacity, locked, booked)
values ('schedule-today-neuro-001', 30, 0, 10)
on conflict (schedule_id) do nothing;

-- ──────────────────────────────────────────────────────────────────
-- 3. 今日候诊患者（张医生 / doctor-001 / 神经内科）
--    business_no 列有 DEFAULT，可省略；status 覆盖时直接更新
-- ──────────────────────────────────────────────────────────────────
insert into appointment.appointment (
  id, schedule_id, patient_id, patient_name, doctor_id, doctor_name,
  department_id, department_name, visit_date, period, start_time,
  source, status, payment_status, payment_method,
  triage_summary, risk_level, queue_number, missed_count, paid_at
) values
  ('appt-today-001', 'schedule-today-neuro-001', 'pat-today-001', '刘建国',
   'doctor-001', '张医生', 'dept-neuro', '神经内科',
   current_date, '上午', '08:00',
   'OFFLINE', 'WAITING', 'PAID', 'OFFLINE_WINDOW',
   '主诉：反复头晕3天，血压偏高（150/95mmHg），既往高血压病史5年', 'MEDIUM',
   1, 0, now()),

  ('appt-today-002', 'schedule-today-neuro-001', 'pat-today-002', '张秀英',
   'doctor-001', '张医生', 'dept-neuro', '神经内科',
   current_date, '上午', '08:15',
   'ONLINE', 'WAITING', 'PAID', 'WECHAT',
   '主诉：失眠2周，入睡困难，伴焦虑情绪，日间头痛', 'LOW',
   2, 0, now()),

  ('appt-today-003', 'schedule-today-neuro-001', 'pat-today-003', '王大力',
   'doctor-001', '张医生', 'dept-neuro', '神经内科',
   current_date, '上午', '08:30',
   'ONLINE', 'WAITING', 'PAID', 'ALIPAY',
   '主诉：持续头痛3天，颈部酸痛，伴恶心，无发热', 'MEDIUM',
   3, 0, now()),

  ('appt-today-004', 'schedule-today-neuro-001', 'pat-today-004', '陈小梅',
   'doctor-001', '张医生', 'dept-neuro', '神经内科',
   current_date, '上午', '09:00',
   'ONLINE', 'CALLED', 'PAID', 'WECHAT',
   '脑梗死后随访，左侧肢体活动较前好转，服用阿司匹林+他汀', 'HIGH',
   4, 0, now()),

  ('appt-today-005', 'schedule-today-neuro-001', 'pat-today-005', '李明远',
   'doctor-001', '张医生', 'dept-neuro', '神经内科',
   current_date, '上午', '09:30',
   'OFFLINE', 'IN_VISIT', 'PAID', 'OFFLINE_WINDOW',
   '主诉：发作性眩晕2天，视物模糊，站立不稳，排除前庭疾患', 'HIGH',
   5, 0, now()),

  ('appt-today-006', 'schedule-today-neuro-001', 'pat-today-006', '赵晓燕',
   'doctor-001', '张医生', 'dept-neuro', '神经内科',
   current_date, '上午', '10:00',
   'ONLINE', 'WAITING', 'PAID', 'WECHAT',
   '偏头痛急性发作，视觉先兆（闪光暗点），持续约2小时', 'MEDIUM',
   6, 0, now()),

  ('appt-today-007', 'schedule-today-neuro-001', 'pat-today-007', '孙志强',
   'doctor-001', '张医生', 'dept-neuro', '神经内科',
   current_date, '上午', '10:30',
   'OFFLINE', 'WAITING', 'PAID', 'OFFLINE_WINDOW',
   '帕金森病随访，服用左旋多巴，症状控制尚可，近期出现轻微幻觉', 'HIGH',
   7, 0, now()),

  ('appt-today-008', 'schedule-today-neuro-001', 'pat-today-008', '周芳芳',
   'doctor-001', '张医生', 'dept-neuro', '神经内科',
   current_date, '上午', '11:00',
   'ONLINE', 'REVISIT_WAITING', 'PAID', 'WECHAT',
   '已完成头颅MRI，携带报告复诊，初诊考虑后循环缺血', 'HIGH',
   8, 0, now()),

  ('appt-today-009', 'schedule-today-neuro-001', 'pat-today-009', '吴德强',
   'doctor-001', '张医生', 'dept-neuro', '神经内科',
   current_date, '上午', '08:00',
   'ONLINE', 'FINISHED', 'PAID', 'WECHAT',
   '三叉神经痛复诊，卡马西平加量后疼痛明显减轻', 'LOW',
   9, 0, now()),

  ('appt-today-010', 'schedule-today-neuro-001', 'pat-today-010', '郑玉兰',
   'doctor-001', '张医生', 'dept-neuro', '神经内科',
   current_date, '上午', '08:15',
   'OFFLINE', 'FINISHED', 'PAID', 'OFFLINE_WINDOW',
   '癫痫随访，末次发作6个月前，丙戊酸钠血药浓度达标', 'MEDIUM',
   10, 0, now())

on conflict (id) do update
  set status       = excluded.status,
      queue_number = excluded.queue_number,
      triage_summary = excluded.triage_summary;

-- 补充病历（medical_record schema）—— 让医生打开患者时有AI预填内容
insert into medical_record.medical_record (
  id, appointment_id, patient_id, patient_name,
  doctor_id, doctor_name, department_name, visit_date, period,
  ai_triage_summary, ai_risk_level,
  chief_complaint, present_illness, past_history, allergy_history,
  preliminary_diagnosis, diagnosis, treatment_plan,
  status, version, created_at, updated_at
) values
  ('mr-today-001','appt-today-001','pat-today-001','刘建国',
   'doctor-001','张医生','神经内科', current_date,'上午',
   '反复头晕3天，血压偏高（150/95mmHg），既往高血压病史5年','MEDIUM',
   '反复头晕3天','患者3天前无明显诱因出现头晕，伴视物旋转，与体位改变有关，血压150/95mmHg。无耳鸣，无恶心呕吐。',
   '高血压病史5年，规律服药','无药物过敏',
   '1.良性位置性眩晕 2.高血压病3级',
   '','予耳石复位治疗；调整降压方案，加用氨氯地平5mg qd',
   'DRAFT', 0, now(), now()),

  ('mr-today-002','appt-today-002','pat-today-002','张秀英',
   'doctor-001','张医生','神经内科', current_date,'上午',
   '失眠2周，入睡困难，伴焦虑情绪，日间头痛','LOW',
   '失眠2周','2周前因工作压力出现失眠，入睡困难，睡眠浅，每晚睡眠约3-4小时，伴焦虑、情绪低落，日间头痛明显。',
   '无特殊','无药物过敏',
   '1.失眠症 2.焦虑状态',
   '','建议认知行为治疗；予艾司唑仑0.5mg qn短期使用；必要时转诊心理科',
   'DRAFT', 0, now(), now()),

  ('mr-today-003','appt-today-003','pat-today-003','王大力',
   'doctor-001','张医生','神经内科', current_date,'上午',
   '持续头痛3天，颈部酸痛，伴恶心，无发热','MEDIUM',
   '头痛3天，颈部酸痛','头痛呈持续性钝痛，以双侧颞部及枕部为主，颈部肌肉紧张酸痛，长期伏案工作史，伴轻度恶心，无呕吐，无发热，无意识障碍。',
   '无特殊','青霉素过敏',
   '1.紧张型头痛 2.颈椎病',
   '','予布洛芬缓释胶囊0.3g bid；甲钴胺0.5mg tid；颈部理疗；嘱改善坐姿',
   'DRAFT', 0, now(), now()),

  ('mr-today-004','appt-today-004','pat-today-004','陈小梅',
   'doctor-001','张医生','神经内科', current_date,'上午',
   '脑梗死后随访，左侧肢体活动较前好转','HIGH',
   '脑梗死后随访','患者2个月前因急性脑梗死住院，目前规律服用阿司匹林100mg qd、阿托伐他汀20mg qn，左侧上肢肌力4级，步态尚稳。血压130/80mmHg。',
   '高血压、糖尿病','无药物过敏',
   '脑梗死恢复期',
   '','继续原方案；加强康复锻炼；3个月后复查头颅MRI及血脂',
   'DRAFT', 0, now(), now())

on conflict (id) do nothing;

commit;
