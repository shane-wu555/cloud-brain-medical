-- ══════════════════════════════════════════════════════════════════════
-- seed-medical-tech-workbench.sql
-- 修复医技工作台队列为空问题，并插入今日测试医嘱数据
--
-- 问题根因：V4 migration 写入的 medical_technician.id 是
--   'doctor-check-001' / 'doctor-lab-001' / 'doctor-disposal-001'，
--   但实际 auth.user_account.id 是
--   'doctor-002' / 'doctor-lab-01' / 'doctor-disp-01'。
--   service 通过 actorId（JWT sub）查 medical_technician 找不到记录，
--   导致 workspace_id 为空，医嘱全部被过滤掉。
--
-- 执行方式：
--   psql -h <host> -U <user> -d <db> -f seed-medical-tech-workbench.sql
-- ══════════════════════════════════════════════════════════════════════

begin;

-- ──────────────────────────────────────────────────────────────────────
-- 1. 修复 medical_technician：写入与真实 user_account.id 对应的记录
-- ──────────────────────────────────────────────────────────────────────
insert into medical_technician (id, employee_no, name, role_type, workspace_id)
values
  -- CHECK_DOCTOR（影像检查科，共用 doctor-check-001 工作站）
  ('doctor-002',        '00030001', '李医生', 'CHECK_DOCTOR',    'doctor-check-001'),
  ('doctor-imaging-02', '00030002', '高医生', 'CHECK_DOCTOR',    'doctor-check-001'),
  ('doctor-imaging-03', '00030003', '马医生', 'CHECK_DOCTOR',    'doctor-check-001'),
  -- LAB_DOCTOR（检验科，共用 doctor-lab-001 工作站）
  ('doctor-lab-01',     '00120001', '方技师', 'LAB_DOCTOR',      'doctor-lab-001'),
  ('doctor-lab-02',     '00120002', '谢技师', 'LAB_DOCTOR',      'doctor-lab-001'),
  ('doctor-lab-03',     '00120003', '曹技师', 'LAB_DOCTOR',      'doctor-lab-001'),
  -- DISPOSAL_DOCTOR（处置室，共用 doctor-disposal-001 工作站）
  ('doctor-disp-01',    '00140001', '邓护士', 'DISPOSAL_DOCTOR', 'doctor-disposal-001'),
  ('doctor-disp-02',    '00140002', '傅护士', 'DISPOSAL_DOCTOR', 'doctor-disposal-001')
on conflict (id) do update
  set employee_no  = excluded.employee_no,
      name         = excluded.name,
      role_type    = excluded.role_type,
      workspace_id = excluded.workspace_id,
      active       = true;

-- ──────────────────────────────────────────────────────────────────────
-- 2. 今日医技医嘱（引用已有的 appt-today-* 挂号记录）
--    status='WAITING' + payment_status='PAID'，医技工作台才会显示
-- ──────────────────────────────────────────────────────────────────────
insert into medical_order (
  id, appointment_id, patient_id, patient_name, ordering_doctor_id,
  order_type, project_code, project_name, purpose, body_part,
  amount, payment_status, status,
  executor_id, executor_name, execution_location, equipment_id,
  executor_workspace_id, executor_workspace_name, executor_workspace_location,
  urgency, queue_number, missed_count,
  triage_created_by_type, triage_reasons,
  created_at
) values

  -- ── CHECK 医嘱 → 检查科（workspace: doctor-check-001）──
  ('mo-check-001',
   'appt-today-005', 'pat-today-005', '李明远', 'doctor-001',
   'CHECK', 'MRI-BRAIN', '颅脑MRI平扫+增强',
   '发作性眩晕，排除后颅窝占位及血管性病变', '颅脑',
   680.00, 'PAID', 'WAITING',
   'doctor-check-001', '检查科MRI室', '医技楼2层MRI室', 'MRI-01',
   'doctor-check-001', '检查科MRI室', '医技楼2层MRI室',
   'EMERGENCY', 1, 0,
   'AI', '眩晕急性发作，优先排除后颅窝血管性或占位性病变',
   now()),

  ('mo-check-002',
   'appt-today-004', 'pat-today-004', '陈小梅', 'doctor-001',
   'CHECK', 'CT-HEAD', '头颅CT平扫',
   '脑梗死随访，评估梗死灶演变及新发病灶', '颅脑',
   280.00, 'PAID', 'WAITING',
   'doctor-check-001', '检查科CT室', '医技楼2层CT室', 'CT-01',
   'doctor-check-001', '检查科CT室', '医技楼2层CT室',
   'ROUTINE', 2, 0,
   'AI', '脑梗死随访，常规CT评估',
   now()),

  ('mo-check-003',
   'appt-today-007', 'pat-today-007', '孙志强', 'doctor-001',
   'CHECK', 'CT-HEAD', '头颅CT平扫',
   '帕金森病随访，评估脑萎缩及基底节形态', '颅脑',
   280.00, 'PAID', 'WAITING',
   'doctor-check-001', '检查科CT室', '医技楼2层CT室', 'CT-01',
   'doctor-check-001', '检查科CT室', '医技楼2层CT室',
   'ROUTINE', 3, 0,
   'AI', '帕金森随访，常规影像评估',
   now()),

  ('mo-check-004',
   'appt-today-001', 'pat-today-001', '刘建国', 'doctor-001',
   'CHECK', 'CT-HEAD', '颈部血管超声',
   '高血压反复头晕，排除颈动脉狭窄', '颈部血管',
   350.00, 'PAID', 'WAITING',
   'doctor-check-001', '检查科超声室', '医技楼2层超声室', 'US-01',
   'doctor-check-001', '检查科超声室', '医技楼2层超声室',
   'ROUTINE', 4, 0,
   'AI', '高血压头晕，超声评估颈动脉狭窄程度',
   now()),

  -- ── LAB 医嘱 → 检验科（workspace: doctor-lab-001）──
  ('mo-lab-001',
   'appt-today-002', 'pat-today-002', '张秀英', 'doctor-001',
   'LAB', 'CBC', '血常规+超敏CRP',
   '失眠焦虑，排除贫血及隐性感染', null,
   45.00, 'PAID', 'WAITING',
   'doctor-lab-001', '检验科', '医技楼1层检验科', 'LAB-01',
   'doctor-lab-001', '检验科', '医技楼1层检验科',
   'ROUTINE', 1, 0,
   'AI', '常规血液学检查',
   now()),

  ('mo-lab-002',
   'appt-today-003', 'pat-today-003', '王大力', 'doctor-001',
   'LAB', 'LIVER', '生化全套+凝血四项',
   '持续头痛，全面生化排查', null,
   178.00, 'PAID', 'WAITING',
   'doctor-lab-001', '检验科', '医技楼1层检验科', 'LAB-01',
   'doctor-lab-001', '检验科', '医技楼1层检验科',
   'ROUTINE', 2, 0,
   'AI', '头痛综合评估，生化排查肝肾功能',
   now()),

  ('mo-lab-003',
   'appt-today-006', 'pat-today-006', '赵晓燕', 'doctor-001',
   'LAB', 'CBC', '血常规',
   '偏头痛，排除血液系统继发因素', null,
   28.00, 'PAID', 'WAITING',
   'doctor-lab-001', '检验科', '医技楼1层检验科', 'LAB-01',
   'doctor-lab-001', '检验科', '医技楼1层检验科',
   'ROUTINE', 3, 0,
   'AI', '偏头痛常规血液检查',
   now()),

  -- ── DISPOSAL 医嘱 → 处置室（workspace: doctor-disposal-001）──
  ('mo-disp-001',
   'appt-today-005', 'pat-today-005', '李明远', 'doctor-001',
   'DISPOSAL', 'DISP-INFUSION', '静脉滴注甘露醇250ml',
   '眩晕急性期，静脉用药降低颅内压', null,
   35.00, 'PAID', 'WAITING',
   'doctor-disposal-001', '处置室', '门诊楼3层处置室', null,
   'doctor-disposal-001', '处置室', '门诊楼3层处置室',
   'ROUTINE', 1, 0,
   'AI', '急性眩晕静脉用药处置',
   now()),

  ('mo-disp-002',
   'appt-today-006', 'pat-today-006', '赵晓燕', 'doctor-001',
   'DISPOSAL', 'DISP-INFUSION', '肌肉注射苯海拉明20mg',
   '偏头痛伴恶心，止吐对症处理', null,
   18.00, 'PAID', 'WAITING',
   'doctor-disposal-001', '处置室', '门诊楼3层处置室', null,
   'doctor-disposal-001', '处置室', '门诊楼3层处置室',
   'ROUTINE', 2, 0,
   'AI', '偏头痛止吐对症处置',
   now())

on conflict (id) do update
  set status           = excluded.status,
      payment_status   = excluded.payment_status,
      queue_number     = excluded.queue_number,
      urgency          = excluded.urgency,
      triage_reasons   = excluded.triage_reasons;

commit;
