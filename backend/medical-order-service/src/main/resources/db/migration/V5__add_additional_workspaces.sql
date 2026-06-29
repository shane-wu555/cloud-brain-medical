-- ══════════════════════════════════════════════════════════════════════
-- V5: 为每种医技类型新增 2 个诊室，使 AI 分诊有真实的调度决策空间。
--
-- CHECK（检查）：
--   doctor-check-001  综合影像室  CT+MRI（已有）
--   workspace-check-002  CT专科室   仅CT，容量较小
--   workspace-check-003  MRI专科室  仅MRI，容量更小
--
-- LAB（检验）：
--   doctor-lab-001    综合检验室  CBC+LIVER+神经免疫（已有）
--   workspace-lab-002 常规检验室B CBC+LIVER
--   workspace-lab-003 急诊检验室  仅CBC，容量小但优先保障急诊
--
-- DISPOSAL（处置）：
--   doctor-disposal-001    综合处置室  输液+换药+康复（已有）
--   workspace-disposal-002 输液处置室B 仅输液
--   workspace-disposal-003 急诊处置室  仅输液，低容量急诊专用
-- ══════════════════════════════════════════════════════════════════════

-- ── 1. 新增诊室 ──────────────────────────────────────────────────────
insert into medical_workspace (id, room_code, name, workspace_type, specialties, location, equipment_ids, capacity, active)
values
  -- CHECK
  ('workspace-check-002', 'workspace-check-002', 'CT专科室',    'CHECK', '头部CT,神经影像',  '医技楼2层CT室B',   'CT-02',  15, true),
  ('workspace-check-003', 'workspace-check-003', 'MRI专科室',   'CHECK', '颅脑MRI,神经影像', '医技楼3层MRI室',   'MRI-02', 10, true),
  -- LAB
  ('workspace-lab-002',   'workspace-lab-002',   '常规检验室B', 'LAB',   '血常规,生化检验',  '医技楼1层检验科B', 'LAB-02', 30, true),
  ('workspace-lab-003',   'workspace-lab-003',   '急诊检验室',  'LAB',   '血常规',           '急诊楼2层急检室',  'LAB-03', 15, true),
  -- DISPOSAL
  ('workspace-disposal-002', 'workspace-disposal-002', '输液处置室B', 'DISPOSAL', '静脉输液', '门诊楼3层处置室B', null, 25, true),
  ('workspace-disposal-003', 'workspace-disposal-003', '急诊处置室',  'DISPOSAL', '静脉输液', '急诊楼1层处置室',  null, 15, true)
on conflict (id) do update
  set room_code      = excluded.room_code,
      name           = excluded.name,
      workspace_type = excluded.workspace_type,
      specialties    = excluded.specialties,
      location       = excluded.location,
      equipment_ids  = excluded.equipment_ids,
      capacity       = excluded.capacity,
      active         = excluded.active;

-- ── 2. 绑定各诊室可执行的项目 ──────────────────────────────────────────
insert into medical_workspace_project (workspace_id, project_code, project_name, priority, active)
values
  -- workspace-check-002：CT专科，只做CT
  ('workspace-check-002', 'CT-HEAD',   '头部CT平扫',   10, true),
  ('workspace-check-002', '头部CT',    '头部CT',        20, true),
  ('workspace-check-002', '神经影像',   '神经影像',      30, true),

  -- workspace-check-003：MRI专科，只做MRI
  ('workspace-check-003', 'MRI-BRAIN', '颅脑MRI',      10, true),
  ('workspace-check-003', '颅脑MRI',   '颅脑MRI',       20, true),
  ('workspace-check-003', '神经影像',   '神经影像',      30, true),

  -- workspace-lab-002：常规检验，CBC+LIVER
  ('workspace-lab-002', 'CBC',       '血常规',          10, true),
  ('workspace-lab-002', 'LIVER',     '肝功能',          20, true),
  ('workspace-lab-002', '血常规',    '血常规',           30, true),
  ('workspace-lab-002', '生化检验',  '生化检验',         40, true),

  -- workspace-lab-003：急诊检验，仅CBC（快速出结果）
  ('workspace-lab-003', 'CBC',       '血常规（急检）',  10, true),
  ('workspace-lab-003', '血常规',    '血常规',           20, true),

  -- workspace-disposal-002：普通输液处置
  ('workspace-disposal-002', 'DISP-INFUSION', '静脉输液处置', 10, true),
  ('workspace-disposal-002', '静脉输液',       '静脉输液',      20, true),

  -- workspace-disposal-003：急诊处置
  ('workspace-disposal-003', 'DISP-INFUSION', '静脉输液处置（急诊）', 10, true),
  ('workspace-disposal-003', '静脉输液',       '静脉输液',              20, true)
on conflict (workspace_id, project_code) do update
  set project_name = excluded.project_name,
      priority     = excluded.priority,
      active       = excluded.active;

-- ── 3. 新增医技人员（id 与 auth.user_account.id 对应）────────────────
insert into medical_technician (id, employee_no, name, role_type, workspace_id, active)
values
  ('workspace-check-002',    'D0003', '吴影像师', 'CHECK_DOCTOR',    'workspace-check-002',    true),
  ('workspace-check-003',    'D0004', '冯影像师', 'CHECK_DOCTOR',    'workspace-check-003',    true),
  ('workspace-lab-002',      'L0002', '郑检验师', 'LAB_DOCTOR',      'workspace-lab-002',      true),
  ('workspace-lab-003',      'L0003', '王急检师', 'LAB_DOCTOR',      'workspace-lab-003',      true),
  ('workspace-disposal-002', 'T0002', '周处置师', 'DISPOSAL_DOCTOR', 'workspace-disposal-002', true),
  ('workspace-disposal-003', 'T0003', '徐急处师', 'DISPOSAL_DOCTOR', 'workspace-disposal-003', true)
on conflict (id) do update
  set employee_no  = excluded.employee_no,
      name         = excluded.name,
      role_type    = excluded.role_type,
      workspace_id = excluded.workspace_id,
      active       = excluded.active;
