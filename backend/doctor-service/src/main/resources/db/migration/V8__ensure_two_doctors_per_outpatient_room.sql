-- Ensure every seeded outpatient room has at least two outpatient doctors.
-- No schedules, slots, or appointments are added here.
insert into staff (id, name, employee_no, department_id, role_type, title, specialty) values
  ('00010005','赵医生','00010005','dept-neuro',      'OUTPATIENT_DOCTOR','主治医师',    '眩晕、睡眠障碍、周围神经病'),
  ('00010006','周医生','00010006','dept-neuro',      'OUTPATIENT_DOCTOR','主治医师',    '脑血管病随访、头痛、认知障碍'),
  ('00020004','吴医生','00020004','dept-general',    'OUTPATIENT_DOCTOR','主治医师',    '慢病管理、健康评估、常见感染'),
  ('00030003','郑医生','00030003','dept-ortho',      'OUTPATIENT_DOCTOR','主治医师',    '关节疼痛、运动损伤、骨折复诊'),
  ('00030004','何医生','00030004','dept-ortho',      'OUTPATIENT_DOCTOR','主治医师',    '颈肩腰腿痛、骨质疏松、康复指导'),
  ('00040003','高医生','00040003','dept-cardio',     'OUTPATIENT_DOCTOR','主治医师',    '高血压、冠心病、心衰随访'),
  ('00040004','马医生','00040004','dept-cardio',     'OUTPATIENT_DOCTOR','主治医师',    '心律失常、胸痛评估、心电图判读'),
  ('00050003','胡医生','00050003','dept-gastro',     'OUTPATIENT_DOCTOR','主治医师',    '胃食管反流、消化不良、幽门螺杆菌管理'),
  ('00050004','唐医生','00050004','dept-gastro',     'OUTPATIENT_DOCTOR','主治医师',    '肝胆疾病、肠炎、腹痛评估'),
  ('00060003','罗医生','00060003','dept-pulmo',      'OUTPATIENT_DOCTOR','主治医师',    '慢性咳嗽、哮喘、肺结节随访'),
  ('00060004','宋医生','00060004','dept-pulmo',      'OUTPATIENT_DOCTOR','主治医师',    '肺炎、呼吸衰竭、慢阻肺管理'),
  ('00070003','邓医生','00070003','dept-endo',       'OUTPATIENT_DOCTOR','主治医师',    '糖尿病、甲状腺疾病、肥胖症'),
  ('00070004','秦医生','00070004','dept-endo',       'OUTPATIENT_DOCTOR','主治医师',    '骨代谢疾病、高脂血症、内分泌随访'),
  ('00080004','陆医生','00080004','dept-neuro-surg', 'OUTPATIENT_DOCTOR','主治医师',    '颅脑外伤、脑积水、术后复诊')
on conflict (id) do nothing;

insert into outpatient_doctor (staff_id, room_id) values
  ('00010005','room-neuro-02'),
  ('00010006','room-neuro-03'),
  ('00020004','room-general-02'),
  ('00030003','room-ortho-01'),
  ('00030004','room-ortho-02'),
  ('00040003','room-cardio-01'),
  ('00040004','room-cardio-02'),
  ('00050003','room-gastro-01'),
  ('00050004','room-gastro-02'),
  ('00060003','room-pulmo-01'),
  ('00060004','room-pulmo-02'),
  ('00070003','room-endo-01'),
  ('00070004','room-endo-02'),
  ('00080004','room-neuro-surg-02')
on conflict (staff_id) do nothing;
