-- 为现有诊室补充多名门诊医生，便于验证同诊室请假/手术后的重排逻辑
insert into staff (id, name, employee_no, department_id, role_type, title, specialty) values
  ('00010004','赵医生','00010004','dept-neuro',     'OUTPATIENT_DOCTOR','副主任医师','眩晕、偏头痛、周围神经病'),
  ('00020003','周医生','00020003','dept-general',   'OUTPATIENT_DOCTOR','主治医师',  '发热、常见慢病随访、老年综合评估'),
  ('00080003','彭医生','00080003','dept-neuro-surg','OUTPATIENT_DOCTOR','主治医师',  '脑积水、功能神经外科、术后随访')
on conflict (id) do nothing;

insert into outpatient_doctor (staff_id, room_id) values
  ('00010004','room-neuro-01'),
  ('00020003','room-general-01'),
  ('00080003','room-neuro-surg-01')
on conflict (staff_id) do nothing;
