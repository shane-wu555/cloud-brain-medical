begin;

-- Doctor/schedule smoke data for local QA.
-- Creates:
-- - 8 outpatient departments
-- - 48 outpatient doctors
-- - realistic 21-day schedule coverage
-- - concrete start-time slots and matching appointment inventory
--
-- Known password for seeded doctor accounts: abc12345

insert into doctor.department (id, name, description, active)
values
    ('dept-smoke-cardiology', '心血管内科', '高血压、冠心病、心律失常、心衰随访', true),
    ('dept-smoke-respiratory', '呼吸与危重症医学科', '咳嗽、哮喘、慢阻肺、肺部感染', true),
    ('dept-smoke-endocrine', '内分泌科', '糖尿病、甲状腺疾病、代谢综合征', true),
    ('dept-smoke-digestive', '消化内科', '胃肠疾病、肝胆胰疾病、消化内镜随访', true),
    ('dept-smoke-orthopedics', '骨科', '颈肩腰腿痛、关节疾病、骨折术后复查', true),
    ('dept-smoke-dermatology', '皮肤科', '湿疹、荨麻疹、痤疮、皮肤感染', true),
    ('dept-smoke-pediatrics', '儿科', '儿童发热、咳嗽、腹泻、生长发育咨询', true),
    ('dept-smoke-ent', '耳鼻咽喉科', '鼻炎、咽喉炎、中耳炎、眩晕耳鸣', true)
on conflict (id) do update
set name = excluded.name,
    description = excluded.description,
    active = excluded.active;

with doctor_seed(id, username, phone, name, title, department_id, specialty) as (
    values
        ('doc-smoke-card-01', 'doctor-card-01', '13701010001', '陈心内', '主任医师', 'dept-smoke-cardiology', '冠心病、胸痛评估、支架术后随访'),
        ('doc-smoke-card-02', 'doctor-card-02', '13701010002', '刘心内', '副主任医师', 'dept-smoke-cardiology', '高血压、心衰、房颤管理'),
        ('doc-smoke-card-03', 'doctor-card-03', '13701010003', '王心内', '主治医师', 'dept-smoke-cardiology', '心悸、血脂异常、心电图异常'),
        ('doc-smoke-card-04', 'doctor-card-04', '13701010004', '赵心内', '主治医师', 'dept-smoke-cardiology', '心律失常、动态心电图解读'),
        ('doc-smoke-card-05', 'doctor-card-05', '13701010005', '孙心内', '副主任医师', 'dept-smoke-cardiology', '老年心血管病、慢病复诊'),
        ('doc-smoke-card-06', 'doctor-card-06', '13701010006', '周心内', '医师', 'dept-smoke-cardiology', '高血压初诊、用药调整'),

        ('doc-smoke-resp-01', 'doctor-resp-01', '13701010101', '吴呼吸', '主任医师', 'dept-smoke-respiratory', '慢阻肺、肺结节、哮喘'),
        ('doc-smoke-resp-02', 'doctor-resp-02', '13701010102', '郑呼吸', '副主任医师', 'dept-smoke-respiratory', '咳嗽、支气管炎、肺部感染'),
        ('doc-smoke-resp-03', 'doctor-resp-03', '13701010103', '冯呼吸', '主治医师', 'dept-smoke-respiratory', '哮喘随访、过敏性咳嗽'),
        ('doc-smoke-resp-04', 'doctor-resp-04', '13701010104', '蒋呼吸', '主治医师', 'dept-smoke-respiratory', '发热咳嗽、胸片异常'),
        ('doc-smoke-resp-05', 'doctor-resp-05', '13701010105', '沈呼吸', '副主任医师', 'dept-smoke-respiratory', '肺功能异常、慢性咳嗽'),
        ('doc-smoke-resp-06', 'doctor-resp-06', '13701010106', '韩呼吸', '医师', 'dept-smoke-respiratory', '上呼吸道感染、复诊开药'),

        ('doc-smoke-endo-01', 'doctor-endo-01', '13701010201', '杨内分泌', '主任医师', 'dept-smoke-endocrine', '糖尿病并发症、胰岛素调整'),
        ('doc-smoke-endo-02', 'doctor-endo-02', '13701010202', '朱内分泌', '副主任医师', 'dept-smoke-endocrine', '甲状腺结节、甲亢甲减'),
        ('doc-smoke-endo-03', 'doctor-endo-03', '13701010203', '秦内分泌', '主治医师', 'dept-smoke-endocrine', '2型糖尿病、肥胖管理'),
        ('doc-smoke-endo-04', 'doctor-endo-04', '13701010204', '许内分泌', '主治医师', 'dept-smoke-endocrine', '妊娠糖尿病、血糖波动'),
        ('doc-smoke-endo-05', 'doctor-endo-05', '13701010205', '何内分泌', '副主任医师', 'dept-smoke-endocrine', '骨质疏松、代谢综合征'),
        ('doc-smoke-endo-06', 'doctor-endo-06', '13701010206', '吕内分泌', '医师', 'dept-smoke-endocrine', '糖尿病复诊、检验解读'),

        ('doc-smoke-dige-01', 'doctor-dige-01', '13701010301', '张消化', '主任医师', 'dept-smoke-digestive', '胃食管反流、胃肠镜后复诊'),
        ('doc-smoke-dige-02', 'doctor-dige-02', '13701010302', '梁消化', '副主任医师', 'dept-smoke-digestive', '肝功能异常、脂肪肝'),
        ('doc-smoke-dige-03', 'doctor-dige-03', '13701010303', '邓消化', '主治医师', 'dept-smoke-digestive', '腹痛腹泻、幽门螺杆菌'),
        ('doc-smoke-dige-04', 'doctor-dige-04', '13701010304', '傅消化', '主治医师', 'dept-smoke-digestive', '便秘、胃炎、肠易激'),
        ('doc-smoke-dige-05', 'doctor-dige-05', '13701010305', '曹消化', '副主任医师', 'dept-smoke-digestive', '炎症性肠病、肝胆疾病'),
        ('doc-smoke-dige-06', 'doctor-dige-06', '13701010306', '谢消化', '医师', 'dept-smoke-digestive', '消化不良、报告解读'),

        ('doc-smoke-orth-01', 'doctor-orth-01', '13701010401', '罗骨科', '主任医师', 'dept-smoke-orthopedics', '关节退变、运动损伤'),
        ('doc-smoke-orth-02', 'doctor-orth-02', '13701010402', '宋骨科', '副主任医师', 'dept-smoke-orthopedics', '腰椎间盘突出、颈椎病'),
        ('doc-smoke-orth-03', 'doctor-orth-03', '13701010403', '唐骨科', '主治医师', 'dept-smoke-orthopedics', '骨折术后复查、换药'),
        ('doc-smoke-orth-04', 'doctor-orth-04', '13701010404', '魏骨科', '主治医师', 'dept-smoke-orthopedics', '肩膝关节痛、腱鞘炎'),
        ('doc-smoke-orth-05', 'doctor-orth-05', '13701010405', '姚骨科', '副主任医师', 'dept-smoke-orthopedics', '脊柱退变、骨质疏松'),
        ('doc-smoke-orth-06', 'doctor-orth-06', '13701010406', '毛骨科', '医师', 'dept-smoke-orthopedics', '扭伤、软组织损伤'),

        ('doc-smoke-derm-01', 'doctor-derm-01', '13701010501', '潘皮肤', '主任医师', 'dept-smoke-dermatology', '银屑病、特应性皮炎'),
        ('doc-smoke-derm-02', 'doctor-derm-02', '13701010502', '董皮肤', '副主任医师', 'dept-smoke-dermatology', '痤疮、玫瑰痤疮、色斑'),
        ('doc-smoke-derm-03', 'doctor-derm-03', '13701010503', '袁皮肤', '主治医师', 'dept-smoke-dermatology', '湿疹、荨麻疹、过敏'),
        ('doc-smoke-derm-04', 'doctor-derm-04', '13701010504', '石皮肤', '主治医师', 'dept-smoke-dermatology', '真菌感染、皮肤瘙痒'),
        ('doc-smoke-derm-05', 'doctor-derm-05', '13701010505', '贾皮肤', '副主任医师', 'dept-smoke-dermatology', '皮肤肿物、激光术后复查'),
        ('doc-smoke-derm-06', 'doctor-derm-06', '13701010506', '范皮肤', '医师', 'dept-smoke-dermatology', '常见皮炎、用药咨询'),

        ('doc-smoke-pedi-01', 'doctor-pedi-01', '13701010601', '金儿科', '主任医师', 'dept-smoke-pediatrics', '儿童哮喘、反复呼吸道感染'),
        ('doc-smoke-pedi-02', 'doctor-pedi-02', '13701010602', '孔儿科', '副主任医师', 'dept-smoke-pediatrics', '儿童发热、消化不良'),
        ('doc-smoke-pedi-03', 'doctor-pedi-03', '13701010603', '严儿科', '主治医师', 'dept-smoke-pediatrics', '咳嗽、鼻炎、过敏体质'),
        ('doc-smoke-pedi-04', 'doctor-pedi-04', '13701010604', '邱儿科', '主治医师', 'dept-smoke-pediatrics', '腹泻、喂养、生长发育'),
        ('doc-smoke-pedi-05', 'doctor-pedi-05', '13701010605', '程儿科', '副主任医师', 'dept-smoke-pediatrics', '儿童内分泌、矮小症筛查'),
        ('doc-smoke-pedi-06', 'doctor-pedi-06', '13701010606', '余儿科', '医师', 'dept-smoke-pediatrics', '儿童常见病复诊'),

        ('doc-smoke-ent-01', 'doctor-ent-01', '13701010701', '苏耳鼻喉', '主任医师', 'dept-smoke-ent', '鼻窦炎、过敏性鼻炎'),
        ('doc-smoke-ent-02', 'doctor-ent-02', '13701010702', '叶耳鼻喉', '副主任医师', 'dept-smoke-ent', '咽喉炎、声带疾病'),
        ('doc-smoke-ent-03', 'doctor-ent-03', '13701010703', '白耳鼻喉', '主治医师', 'dept-smoke-ent', '中耳炎、听力下降'),
        ('doc-smoke-ent-04', 'doctor-ent-04', '13701010704', '杜耳鼻喉', '主治医师', 'dept-smoke-ent', '鼻出血、耳鸣眩晕'),
        ('doc-smoke-ent-05', 'doctor-ent-05', '13701010705', '顾耳鼻喉', '副主任医师', 'dept-smoke-ent', '儿童鼾症、扁桃体疾病'),
        ('doc-smoke-ent-06', 'doctor-ent-06', '13701010706', '夏耳鼻喉', '医师', 'dept-smoke-ent', '耳鼻喉常见病复诊')
)
insert into auth.user_account (
    id, username, password, phone, name, role, permissions, real_name_verified, created_at
)
select
    id,
    username,
    '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.',
    phone,
    name,
    'OUTPATIENT_DOCTOR',
    'appointment:read,appointment:skip,medical-record:read,medical-record:write,medical-order:create,prescription:create',
    true,
    now()
from doctor_seed
on conflict (username) do update
set password = excluded.password,
    phone = excluded.phone,
    name = excluded.name,
    role = excluded.role,
    permissions = excluded.permissions,
    real_name_verified = excluded.real_name_verified;

with doctor_seed(id, name, title, department_id, specialty) as (
    values
        ('doc-smoke-card-01', '陈心内', '主任医师', 'dept-smoke-cardiology', '冠心病、胸痛评估、支架术后随访'),
        ('doc-smoke-card-02', '刘心内', '副主任医师', 'dept-smoke-cardiology', '高血压、心衰、房颤管理'),
        ('doc-smoke-card-03', '王心内', '主治医师', 'dept-smoke-cardiology', '心悸、血脂异常、心电图异常'),
        ('doc-smoke-card-04', '赵心内', '主治医师', 'dept-smoke-cardiology', '心律失常、动态心电图解读'),
        ('doc-smoke-card-05', '孙心内', '副主任医师', 'dept-smoke-cardiology', '老年心血管病、慢病复诊'),
        ('doc-smoke-card-06', '周心内', '医师', 'dept-smoke-cardiology', '高血压初诊、用药调整'),
        ('doc-smoke-resp-01', '吴呼吸', '主任医师', 'dept-smoke-respiratory', '慢阻肺、肺结节、哮喘'),
        ('doc-smoke-resp-02', '郑呼吸', '副主任医师', 'dept-smoke-respiratory', '咳嗽、支气管炎、肺部感染'),
        ('doc-smoke-resp-03', '冯呼吸', '主治医师', 'dept-smoke-respiratory', '哮喘随访、过敏性咳嗽'),
        ('doc-smoke-resp-04', '蒋呼吸', '主治医师', 'dept-smoke-respiratory', '发热咳嗽、胸片异常'),
        ('doc-smoke-resp-05', '沈呼吸', '副主任医师', 'dept-smoke-respiratory', '肺功能异常、慢性咳嗽'),
        ('doc-smoke-resp-06', '韩呼吸', '医师', 'dept-smoke-respiratory', '上呼吸道感染、复诊开药'),
        ('doc-smoke-endo-01', '杨内分泌', '主任医师', 'dept-smoke-endocrine', '糖尿病并发症、胰岛素调整'),
        ('doc-smoke-endo-02', '朱内分泌', '副主任医师', 'dept-smoke-endocrine', '甲状腺结节、甲亢甲减'),
        ('doc-smoke-endo-03', '秦内分泌', '主治医师', 'dept-smoke-endocrine', '2型糖尿病、肥胖管理'),
        ('doc-smoke-endo-04', '许内分泌', '主治医师', 'dept-smoke-endocrine', '妊娠糖尿病、血糖波动'),
        ('doc-smoke-endo-05', '何内分泌', '副主任医师', 'dept-smoke-endocrine', '骨质疏松、代谢综合征'),
        ('doc-smoke-endo-06', '吕内分泌', '医师', 'dept-smoke-endocrine', '糖尿病复诊、检验解读'),
        ('doc-smoke-dige-01', '张消化', '主任医师', 'dept-smoke-digestive', '胃食管反流、胃肠镜后复诊'),
        ('doc-smoke-dige-02', '梁消化', '副主任医师', 'dept-smoke-digestive', '肝功能异常、脂肪肝'),
        ('doc-smoke-dige-03', '邓消化', '主治医师', 'dept-smoke-digestive', '腹痛腹泻、幽门螺杆菌'),
        ('doc-smoke-dige-04', '傅消化', '主治医师', 'dept-smoke-digestive', '便秘、胃炎、肠易激'),
        ('doc-smoke-dige-05', '曹消化', '副主任医师', 'dept-smoke-digestive', '炎症性肠病、肝胆疾病'),
        ('doc-smoke-dige-06', '谢消化', '医师', 'dept-smoke-digestive', '消化不良、报告解读'),
        ('doc-smoke-orth-01', '罗骨科', '主任医师', 'dept-smoke-orthopedics', '关节退变、运动损伤'),
        ('doc-smoke-orth-02', '宋骨科', '副主任医师', 'dept-smoke-orthopedics', '腰椎间盘突出、颈椎病'),
        ('doc-smoke-orth-03', '唐骨科', '主治医师', 'dept-smoke-orthopedics', '骨折术后复查、换药'),
        ('doc-smoke-orth-04', '魏骨科', '主治医师', 'dept-smoke-orthopedics', '肩膝关节痛、腱鞘炎'),
        ('doc-smoke-orth-05', '姚骨科', '副主任医师', 'dept-smoke-orthopedics', '脊柱退变、骨质疏松'),
        ('doc-smoke-orth-06', '毛骨科', '医师', 'dept-smoke-orthopedics', '扭伤、软组织损伤'),
        ('doc-smoke-derm-01', '潘皮肤', '主任医师', 'dept-smoke-dermatology', '银屑病、特应性皮炎'),
        ('doc-smoke-derm-02', '董皮肤', '副主任医师', 'dept-smoke-dermatology', '痤疮、玫瑰痤疮、色斑'),
        ('doc-smoke-derm-03', '袁皮肤', '主治医师', 'dept-smoke-dermatology', '湿疹、荨麻疹、过敏'),
        ('doc-smoke-derm-04', '石皮肤', '主治医师', 'dept-smoke-dermatology', '真菌感染、皮肤瘙痒'),
        ('doc-smoke-derm-05', '贾皮肤', '副主任医师', 'dept-smoke-dermatology', '皮肤肿物、激光术后复查'),
        ('doc-smoke-derm-06', '范皮肤', '医师', 'dept-smoke-dermatology', '常见皮炎、用药咨询'),
        ('doc-smoke-pedi-01', '金儿科', '主任医师', 'dept-smoke-pediatrics', '儿童哮喘、反复呼吸道感染'),
        ('doc-smoke-pedi-02', '孔儿科', '副主任医师', 'dept-smoke-pediatrics', '儿童发热、消化不良'),
        ('doc-smoke-pedi-03', '严儿科', '主治医师', 'dept-smoke-pediatrics', '咳嗽、鼻炎、过敏体质'),
        ('doc-smoke-pedi-04', '邱儿科', '主治医师', 'dept-smoke-pediatrics', '腹泻、喂养、生长发育'),
        ('doc-smoke-pedi-05', '程儿科', '副主任医师', 'dept-smoke-pediatrics', '儿童内分泌、矮小症筛查'),
        ('doc-smoke-pedi-06', '余儿科', '医师', 'dept-smoke-pediatrics', '儿童常见病复诊'),
        ('doc-smoke-ent-01', '苏耳鼻喉', '主任医师', 'dept-smoke-ent', '鼻窦炎、过敏性鼻炎'),
        ('doc-smoke-ent-02', '叶耳鼻喉', '副主任医师', 'dept-smoke-ent', '咽喉炎、声带疾病'),
        ('doc-smoke-ent-03', '白耳鼻喉', '主治医师', 'dept-smoke-ent', '中耳炎、听力下降'),
        ('doc-smoke-ent-04', '杜耳鼻喉', '主治医师', 'dept-smoke-ent', '鼻出血、耳鸣眩晕'),
        ('doc-smoke-ent-05', '顾耳鼻喉', '副主任医师', 'dept-smoke-ent', '儿童鼾症、扁桃体疾病'),
        ('doc-smoke-ent-06', '夏耳鼻喉', '医师', 'dept-smoke-ent', '耳鼻喉常见病复诊')
)
insert into doctor.doctor (id, name, title, department_id, role_type, specialty, active)
select id, name, title, department_id, 'OUTPATIENT_DOCTOR', specialty, true
from doctor_seed
on conflict (id) do update
set name = excluded.name,
    title = excluded.title,
    department_id = excluded.department_id,
    role_type = excluded.role_type,
    specialty = excluded.specialty,
    active = excluded.active;

with doctor_seed(id, dept_code, doctor_no, department_id, title) as (
    values
        ('doc-smoke-card-01', 'card', 1, 'dept-smoke-cardiology', '主任医师'), ('doc-smoke-card-02', 'card', 2, 'dept-smoke-cardiology', '副主任医师'),
        ('doc-smoke-card-03', 'card', 3, 'dept-smoke-cardiology', '主治医师'), ('doc-smoke-card-04', 'card', 4, 'dept-smoke-cardiology', '主治医师'),
        ('doc-smoke-card-05', 'card', 5, 'dept-smoke-cardiology', '副主任医师'), ('doc-smoke-card-06', 'card', 6, 'dept-smoke-cardiology', '医师'),
        ('doc-smoke-resp-01', 'resp', 1, 'dept-smoke-respiratory', '主任医师'), ('doc-smoke-resp-02', 'resp', 2, 'dept-smoke-respiratory', '副主任医师'),
        ('doc-smoke-resp-03', 'resp', 3, 'dept-smoke-respiratory', '主治医师'), ('doc-smoke-resp-04', 'resp', 4, 'dept-smoke-respiratory', '主治医师'),
        ('doc-smoke-resp-05', 'resp', 5, 'dept-smoke-respiratory', '副主任医师'), ('doc-smoke-resp-06', 'resp', 6, 'dept-smoke-respiratory', '医师'),
        ('doc-smoke-endo-01', 'endo', 1, 'dept-smoke-endocrine', '主任医师'), ('doc-smoke-endo-02', 'endo', 2, 'dept-smoke-endocrine', '副主任医师'),
        ('doc-smoke-endo-03', 'endo', 3, 'dept-smoke-endocrine', '主治医师'), ('doc-smoke-endo-04', 'endo', 4, 'dept-smoke-endocrine', '主治医师'),
        ('doc-smoke-endo-05', 'endo', 5, 'dept-smoke-endocrine', '副主任医师'), ('doc-smoke-endo-06', 'endo', 6, 'dept-smoke-endocrine', '医师'),
        ('doc-smoke-dige-01', 'dige', 1, 'dept-smoke-digestive', '主任医师'), ('doc-smoke-dige-02', 'dige', 2, 'dept-smoke-digestive', '副主任医师'),
        ('doc-smoke-dige-03', 'dige', 3, 'dept-smoke-digestive', '主治医师'), ('doc-smoke-dige-04', 'dige', 4, 'dept-smoke-digestive', '主治医师'),
        ('doc-smoke-dige-05', 'dige', 5, 'dept-smoke-digestive', '副主任医师'), ('doc-smoke-dige-06', 'dige', 6, 'dept-smoke-digestive', '医师'),
        ('doc-smoke-orth-01', 'orth', 1, 'dept-smoke-orthopedics', '主任医师'), ('doc-smoke-orth-02', 'orth', 2, 'dept-smoke-orthopedics', '副主任医师'),
        ('doc-smoke-orth-03', 'orth', 3, 'dept-smoke-orthopedics', '主治医师'), ('doc-smoke-orth-04', 'orth', 4, 'dept-smoke-orthopedics', '主治医师'),
        ('doc-smoke-orth-05', 'orth', 5, 'dept-smoke-orthopedics', '副主任医师'), ('doc-smoke-orth-06', 'orth', 6, 'dept-smoke-orthopedics', '医师'),
        ('doc-smoke-derm-01', 'derm', 1, 'dept-smoke-dermatology', '主任医师'), ('doc-smoke-derm-02', 'derm', 2, 'dept-smoke-dermatology', '副主任医师'),
        ('doc-smoke-derm-03', 'derm', 3, 'dept-smoke-dermatology', '主治医师'), ('doc-smoke-derm-04', 'derm', 4, 'dept-smoke-dermatology', '主治医师'),
        ('doc-smoke-derm-05', 'derm', 5, 'dept-smoke-dermatology', '副主任医师'), ('doc-smoke-derm-06', 'derm', 6, 'dept-smoke-dermatology', '医师'),
        ('doc-smoke-pedi-01', 'pedi', 1, 'dept-smoke-pediatrics', '主任医师'), ('doc-smoke-pedi-02', 'pedi', 2, 'dept-smoke-pediatrics', '副主任医师'),
        ('doc-smoke-pedi-03', 'pedi', 3, 'dept-smoke-pediatrics', '主治医师'), ('doc-smoke-pedi-04', 'pedi', 4, 'dept-smoke-pediatrics', '主治医师'),
        ('doc-smoke-pedi-05', 'pedi', 5, 'dept-smoke-pediatrics', '副主任医师'), ('doc-smoke-pedi-06', 'pedi', 6, 'dept-smoke-pediatrics', '医师'),
        ('doc-smoke-ent-01', 'ent', 1, 'dept-smoke-ent', '主任医师'), ('doc-smoke-ent-02', 'ent', 2, 'dept-smoke-ent', '副主任医师'),
        ('doc-smoke-ent-03', 'ent', 3, 'dept-smoke-ent', '主治医师'), ('doc-smoke-ent-04', 'ent', 4, 'dept-smoke-ent', '主治医师'),
        ('doc-smoke-ent-05', 'ent', 5, 'dept-smoke-ent', '副主任医师'), ('doc-smoke-ent-06', 'ent', 6, 'dept-smoke-ent', '医师')
),
work_days as (
    select day::date as work_date,
           row_number() over (order by day) as day_no
    from generate_series(current_date, current_date + interval '20 days', interval '1 day') day
    where extract(isodow from day) <= 6
),
raw_schedules as (
    select
        'sched-smoke-' || dept_code || '-' || lpad(doctor_no::text, 2, '0') || '-' || to_char(work_date, 'YYYYMMDD') || '-' || lower(period_code) as id,
        d.id as doctor_id,
        d.department_id,
        work_date,
        period,
        case
            when period = '全天' then
                case when title = '主任医师' then 52 when title = '副主任医师' then 46 when title = '主治医师' then 40 else 32 end
            when title = '主任医师' then 28
            when title = '副主任医师' then 24
            when title = '主治医师' then 20
            else 16
        end as capacity,
        case
            when (day_no + doctor_no) % 23 = 0 then 'SUSPENDED'
            else 'PUBLISHED'
        end as status,
        case
            when (day_no + doctor_no) % 23 = 0 then '会议/临时停诊'
            else null
        end as suspension_reason
    from doctor_seed d
    join work_days w on true
    cross join lateral (
        values
            ('AM', '上午'),
            ('PM', '下午')
    ) p(period_code, period)
    where
        extract(isodow from work_date) < 6
        and not (doctor_no in (5, 6) and (day_no + doctor_no) % 4 = 0)

    union all

    select
        'sched-smoke-' || dept_code || '-' || lpad(doctor_no::text, 2, '0') || '-' || to_char(work_date, 'YYYYMMDD') || '-full' as id,
        d.id,
        d.department_id,
        work_date,
        '全天',
        case when title = '主任医师' then 52 when title = '副主任医师' then 46 when title = '主治医师' then 40 else 32 end,
        'PUBLISHED',
        null
    from doctor_seed d
    join work_days w on extract(isodow from w.work_date) = 6
    where doctor_no in (1, 2, 3)
)
insert into doctor.doctor_schedule (
    id, doctor_id, department_id, work_date, period, capacity, status, suspension_reason, updated_at
)
select id, doctor_id, department_id, work_date, period, capacity, status, suspension_reason, now()
from raw_schedules
on conflict (id) do update
set doctor_id = excluded.doctor_id,
    department_id = excluded.department_id,
    work_date = excluded.work_date,
    period = excluded.period,
    capacity = excluded.capacity,
    status = excluded.status,
    suspension_reason = excluded.suspension_reason,
    updated_at = now();

with templates(period, start_time) as (
    values
        ('上午', time '08:00'), ('上午', time '08:15'), ('上午', time '08:30'), ('上午', time '08:45'),
        ('上午', time '09:00'), ('上午', time '09:15'), ('上午', time '09:30'), ('上午', time '09:45'),
        ('下午', time '14:00'), ('下午', time '14:15'), ('下午', time '14:30'), ('下午', time '14:45'),
        ('下午', time '15:00'), ('下午', time '15:15'), ('下午', time '15:30'), ('下午', time '15:45'),
        ('全天', time '08:00'), ('全天', time '08:15'), ('全天', time '08:30'), ('全天', time '08:45'),
        ('全天', time '09:00'), ('全天', time '09:15'), ('全天', time '09:30'), ('全天', time '09:45'),
        ('全天', time '14:00'), ('全天', time '14:15'), ('全天', time '14:30'), ('全天', time '14:45'),
        ('全天', time '15:00'), ('全天', time '15:15'), ('全天', time '15:30'), ('全天', time '15:45')
),
expanded as (
    select
        s.id as schedule_id,
        t.start_time,
        s.capacity,
        count(*) over (partition by s.id) as slot_count,
        row_number() over (partition by s.id order by t.start_time) as slot_index
    from doctor.doctor_schedule s
    join templates t on t.period = s.period
    where s.id like 'sched-smoke-%'
),
allocated as (
    select
        schedule_id,
        start_time,
        capacity / slot_count
            + case when slot_index <= capacity % slot_count then 1 else 0 end as slot_capacity
    from expanded
)
insert into doctor.doctor_schedule_time_slot (id, schedule_id, start_time, capacity)
select
    schedule_id || '-' || to_char(start_time, 'HH24MI'),
    schedule_id,
    start_time,
    greatest(1, slot_capacity)
from allocated
on conflict (schedule_id, start_time) do update
set capacity = excluded.capacity;

with inventory_seed as (
    select
        ts.id,
        ts.capacity,
        case
            when s.status = 'SUSPENDED' then ts.capacity
            when abs(hashtext(ts.id)) % 11 = 0 then ts.capacity
            when abs(hashtext(ts.id)) % 7 = 0 then greatest(0, ts.capacity - 1)
            when abs(hashtext(ts.id)) % 5 = 0 then greatest(0, ts.capacity / 2)
            when abs(hashtext(ts.id)) % 3 = 0 then greatest(0, ts.capacity / 3)
            else 0
        end as booked
    from doctor.doctor_schedule_time_slot ts
    join doctor.doctor_schedule s on s.id = ts.schedule_id
    where ts.schedule_id like 'sched-smoke-%'
)
insert into appointment.slot_inventory (schedule_id, capacity, locked, booked)
select
    id,
    capacity,
    case
        when booked < capacity and abs(hashtext(id)) % 17 = 0 then 1
        else 0
    end as locked,
    booked
from inventory_seed
on conflict (schedule_id) do update
set capacity = excluded.capacity,
    locked = least(excluded.locked, excluded.capacity),
    booked = least(excluded.booked, excluded.capacity);

commit;
