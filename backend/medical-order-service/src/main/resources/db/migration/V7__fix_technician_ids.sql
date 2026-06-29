-- ══════════════════════════════════════════════════════════════════════
-- V7: 修正 medical_technician 表，确保人员 ID = doctor.id = auth.user_account.id。
--
-- 清理来源：
--   V4 写入的占位记录（doctor-check-001 / doctor-lab-001 / doctor-disposal-001）
--   旧 V5 可能写入的工作室 ID 冒充人员 ID 的记录
--
-- 技术员绑定策略：
--   优先按 doctor-00X ID 查找；若数据库中已有同工号但不同 ID 的医生
--  （通过管理界面创建），则使用该医生的实际 ID，保证绑定正确。
-- ══════════════════════════════════════════════════════════════════════

-- ── 1. 清理全部错误的工作室 ID 冒充人员 ID 的记录 ─────────────────────
delete from medical_technician
where id in (
    'doctor-check-001', 'doctor-lab-001', 'doctor-disposal-001',
    'workspace-check-002', 'workspace-check-003',
    'workspace-lab-002',   'workspace-lab-003',
    'workspace-disposal-002', 'workspace-disposal-003'
);

-- ── 2. 新增第二工作室（如尚未存在）────────────────────────────────────
insert into medical_workspace (id, room_code, name, workspace_type, specialties, location, equipment_ids, capacity, active)
values
  ('room-check-02',    'room-check-02',    'CT专科室',    'CHECK',    '头部CT,神经影像',  '医技楼2层CT室B',   'CT-02',  15, true),
  ('room-lab-02',      'room-lab-02',      '常规检验室B', 'LAB',      '血常规,生化检验',  '医技楼1层检验科B', 'LAB-02', 30, true),
  ('room-disposal-02', 'room-disposal-02', '输液处置室B', 'DISPOSAL', '静脉输液',         '门诊楼3层处置室B', null,     25, true)
on conflict (id) do nothing;

insert into medical_workspace_project (workspace_id, project_code, project_name, priority, active)
values
  ('room-check-02', 'CT-HEAD',        '头部CT平扫', 10, true),
  ('room-check-02', '头部CT',         '头部CT',      20, true),
  ('room-check-02', '神经影像',       '神经影像',    30, true),
  ('room-lab-02',   'CBC',            '血常规',      10, true),
  ('room-lab-02',   'LIVER',          '肝功能',      20, true),
  ('room-lab-02',   '血常规',         '血常规',       30, true),
  ('room-lab-02',   '生化检验',       '生化检验',    40, true),
  ('room-disposal-02', 'DISP-INFUSION', '静脉输液处置', 10, true),
  ('room-disposal-02', '静脉输液',      '静脉输液',     20, true)
on conflict (workspace_id, project_code) do nothing;

-- ── 3. 按工号绑定技术员，兼容"通过管理界面创建、ID 不固定"的情况 ──────
--    CHECK：工号 00030001（李医生）→ 综合影像室
insert into medical_technician (id, employee_no, name, role_type, workspace_id, active)
select d.id, d.employee_no, d.name, 'CHECK_DOCTOR', 'doctor-check-001', true
from doctor.doctor d where d.employee_no = '00030001'
on conflict (id) do update set workspace_id = 'doctor-check-001', active = true;

--    CHECK：工号 00030002（吴医生）→ CT专科室
insert into medical_technician (id, employee_no, name, role_type, workspace_id, active)
select d.id, d.employee_no, d.name, 'CHECK_DOCTOR', 'room-check-02', true
from doctor.doctor d where d.employee_no = '00030002'
on conflict (id) do update set workspace_id = 'room-check-02', active = true;

--    LAB：工号 00050001（检验科首席）→ 综合检验室
insert into medical_technician (id, employee_no, name, role_type, workspace_id, active)
select d.id, d.employee_no, d.name, 'LAB_DOCTOR', 'doctor-lab-001', true
from doctor.doctor d where d.employee_no = '00050001'
on conflict (id) do update set workspace_id = 'doctor-lab-001', active = true;

--    LAB：工号 00050002（钱医生）→ 常规检验室B
insert into medical_technician (id, employee_no, name, role_type, workspace_id, active)
select d.id, d.employee_no, d.name, 'LAB_DOCTOR', 'room-lab-02', true
from doctor.doctor d where d.employee_no = '00050002'
on conflict (id) do update set workspace_id = 'room-lab-02', active = true;

--    DISPOSAL：工号 00060001（赵医生）→ 综合处置室
insert into medical_technician (id, employee_no, name, role_type, workspace_id, active)
select d.id, d.employee_no, d.name, 'DISPOSAL_DOCTOR', 'doctor-disposal-001', true
from doctor.doctor d where d.employee_no = '00060001'
on conflict (id) do update set workspace_id = 'doctor-disposal-001', active = true;

--    DISPOSAL：工号 00060002（周医生）→ 输液处置室B
insert into medical_technician (id, employee_no, name, role_type, workspace_id, active)
select d.id, d.employee_no, d.name, 'DISPOSAL_DOCTOR', 'room-disposal-02', true
from doctor.doctor d where d.employee_no = '00060002'
on conflict (id) do update set workspace_id = 'room-disposal-02', active = true;
