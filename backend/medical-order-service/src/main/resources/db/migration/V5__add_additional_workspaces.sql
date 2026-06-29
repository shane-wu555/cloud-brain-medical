-- ══════════════════════════════════════════════════════════════════════
-- V5: 修正医技人员设计 + 扩充工作室。
--
-- 设计原则（承接 V4/V8 规范）：
--   medical_workspace.id  → 工作室（地点）
--   medical_technician.id → 医生本人 = auth.user_account.id = doctor.id
--
-- V4 错误：将工作室 ID（doctor-check-001 等）同时用作技术员 ID，导致
--           真实医生账号（doctor-002 等）无法匹配队列。
-- 本迁移删除 V4 的错误占位记录，改为正确的「人 → 工作室」映射。
-- ══════════════════════════════════════════════════════════════════════

-- ── 1. 删除 V4 写入的错误技术员占位记录 ──────────────────────────────
--    这些记录用工作室 ID 冒充人员 ID，不对应任何真实 auth 账号。
delete from medical_technician
where id in ('doctor-check-001', 'doctor-lab-001', 'doctor-disposal-001');

-- ── 2. 新增第二检查室 / 检验室 / 处置室 ──────────────────────────────
--    使用以 room- 为前缀的地点 ID，与人员 ID 明确区分。
insert into medical_workspace (id, room_code, name, workspace_type, specialties, location, equipment_ids, capacity, active)
values
  ('room-check-02', 'room-check-02', 'CT专科室',    'CHECK', '头部CT,神经影像',  '医技楼2层CT室B',   'CT-02',  15, true),
  ('room-lab-02',   'room-lab-02',   '常规检验室B', 'LAB',   '血常规,生化检验',  '医技楼1层检验科B', 'LAB-02', 30, true),
  ('room-disposal-02', 'room-disposal-02', '输液处置室B', 'DISPOSAL', '静脉输液', '门诊楼3层处置室B', null, 25, true)
on conflict (id) do update
  set room_code      = excluded.room_code,
      name           = excluded.name,
      workspace_type = excluded.workspace_type,
      specialties    = excluded.specialties,
      location       = excluded.location,
      equipment_ids  = excluded.equipment_ids,
      capacity       = excluded.capacity,
      active         = excluded.active;

-- ── 3. 新工作室支持的项目 ─────────────────────────────────────────────
insert into medical_workspace_project (workspace_id, project_code, project_name, priority, active)
values
  -- CT专科室：只做 CT
  ('room-check-02', 'CT-HEAD',  '头部CT平扫', 10, true),
  ('room-check-02', '头部CT',   '头部CT',      20, true),
  ('room-check-02', '神经影像', '神经影像',    30, true),
  -- 常规检验室B：CBC + 肝功能
  ('room-lab-02', 'CBC',    '血常规',  10, true),
  ('room-lab-02', 'LIVER',  '肝功能',  20, true),
  ('room-lab-02', '血常规', '血常规',   30, true),
  ('room-lab-02', '生化检验', '生化检验', 40, true),
  -- 输液处置室B
  ('room-disposal-02', 'DISP-INFUSION', '静脉输液处置', 10, true),
  ('room-disposal-02', '静脉输液',       '静脉输液',      20, true)
on conflict (workspace_id, project_code) do update
  set project_name = excluded.project_name,
      priority     = excluded.priority,
      active       = excluded.active;

-- ── 4. 正确的技术员记录：id = doctor.id = auth.user_account.id ────────
insert into medical_technician (id, employee_no, name, role_type, workspace_id, active)
values
  -- 检查科
  ('doctor-002', '00030001', '李医生', 'CHECK_DOCTOR',    'doctor-check-001', true),
  ('doctor-006', '00070001', '吴医生', 'CHECK_DOCTOR',    'room-check-02',    true),
  -- 检验科
  ('doctor-004', '00050001', '王医生', 'LAB_DOCTOR',      'doctor-lab-001',   true),
  ('doctor-007', '00080001', '钱医生', 'LAB_DOCTOR',      'room-lab-02',      true),
  -- 处置科
  ('doctor-005', '00060001', '赵医生', 'DISPOSAL_DOCTOR', 'doctor-disposal-001', true),
  ('doctor-008', '00090001', '周医生', 'DISPOSAL_DOCTOR', 'room-disposal-02',    true)
on conflict (id) do update
  set employee_no  = excluded.employee_no,
      name         = excluded.name,
      role_type    = excluded.role_type,
      workspace_id = excluded.workspace_id,
      active       = excluded.active;
