-- V8: 门诊扩展表与诊室表
--
-- 设计原则：
--   doctor             → 所有医护人员基础表（门诊/检查/检验/药房/处置医生），
--                         id = auth.user_account.id，包含 employee_no
--   outpatient_doctor  → 仅针对 OUTPATIENT_DOCTOR 的扩展信息（科室、诊室）
--   outpatient_clinic_room → 诊室管理
--
-- 注意：不重命名、不删除 doctor 表中的非门诊医生记录。

-- ── 诊室表 ──────────────────────────────────────────────────────
create table if not exists outpatient_clinic_room (
    id            varchar(64) primary key,
    department_id varchar(64) not null references department(id),
    name          varchar(128) not null,
    location      varchar(128),
    active        boolean not null default true,
    unique (department_id, name)
);

-- ── 门诊医生扩展表（1:1 with doctor，仅 OUTPATIENT_DOCTOR）──────
create table if not exists outpatient_doctor (
    doctor_id      varchar(64) primary key references doctor(id),
    clinic_room_id varchar(64) references outpatient_clinic_room(id)
);

-- ── 为每个科室生成默认诊室 ──────────────────────────────────────
insert into outpatient_clinic_room (id, department_id, name, location)
select
    'room-' || id,
    id,
    name || '1号诊室',
    '门诊楼'
from department
on conflict (id) do update
    set name     = excluded.name,
        location = excluded.location;

-- ── 将现有门诊医生写入扩展表 ────────────────────────────────────
insert into outpatient_doctor (doctor_id, clinic_room_id)
select
    d.id,
    'room-' || d.department_id
from doctor d
where d.role_type = 'OUTPATIENT_DOCTOR'
  and exists (
      select 1 from outpatient_clinic_room r
      where r.id = 'room-' || d.department_id
  )
on conflict (doctor_id) do nothing;

comment on table doctor is
    '所有医护人员档案（门诊/检查/检验/药房/处置医生），患者和管理员除外；id = auth.user_account.id。';
comment on column doctor.employee_no is
    '8位纯数字工号，全院唯一，员工登录凭证。';
comment on table outpatient_doctor is
    '门诊医生扩展信息；doctor_id 1:1 对应 doctor.id（仅 role_type=OUTPATIENT_DOCTOR）。';
