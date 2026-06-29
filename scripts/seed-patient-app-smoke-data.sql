begin;

drop index if exists patient.uk_patient_profile_phone;

-- This script is intended for local/manual QA after Flyway has created schemas.
-- It seeds a repeatable dataset for:
-- 1. patient password login / sms login / registration
-- 2. multi-patient profile binding gate
-- 3. richer doctor / department / schedule browsing
-- 4. AI consultation -> recommended department -> online appointment booking
-- 5. pending payments / check-lab reports / disposal flow / prescriptions
--
-- Known password for all seeded accounts below: abc12345
-- BCrypt hash below matches abc12345.

-- 患者账号：无工号（employee_no = NULL）
-- 测试医生账号：username = id，employee_no 为 8 位工号（0001xxxx/0004xxxx/0005xxxx 段，序号从 0010 起避免与主 seed 冲突）
insert into auth.user_account (
    id, username, password, phone, name, role, permissions, real_name_verified, employee_no, created_at
)
values
    ('patient-test-verified-001',   '13800000011', '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13800000011', '测试患者本人',   'PATIENT',          'appointment:create,appointment:cancel,medical-record:read', true,  null,       now()),
    ('patient-test-unverified-001', '13800000012', '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13800000012', '未实名测试患者', 'PATIENT',          'appointment:create,appointment:cancel,medical-record:read', false, null,       now()),
    ('doctor-test-neuro-002',       'doctor-test-neuro-002',    '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000021', '李神内医生', 'OUTPATIENT_DOCTOR', 'appointment:read,appointment:skip,medical-record:read,medical-record:write,medical-order:create,prescription:create', true, '00010010', now()),
    ('doctor-test-neuro-003',       'doctor-test-neuro-003',    '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000022', '王神内医生', 'OUTPATIENT_DOCTOR', 'appointment:read,appointment:skip,medical-record:read,medical-record:write,medical-order:create,prescription:create', true, '00010011', now()),
    ('doctor-test-general-002',     'doctor-test-general-002',  '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000023', '赵全科医生', 'OUTPATIENT_DOCTOR', 'appointment:read,appointment:skip,medical-record:read,medical-record:write,medical-order:create,prescription:create', true, '00040010', now()),
    ('doctor-test-general-003',     'doctor-test-general-003',  '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000024', '孙全科医生', 'OUTPATIENT_DOCTOR', 'appointment:read,appointment:skip,medical-record:read,medical-record:write,medical-order:create,prescription:create', true, '00040011', now()),
    ('doctor-test-rehab-001',       'doctor-test-rehab-001',    '$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.', '13700000025', '周康复医生', 'OUTPATIENT_DOCTOR', 'appointment:read,appointment:skip,medical-record:read,medical-record:write,medical-order:create,prescription:create', true, '00050010', now())
on conflict (username) do update
    set password           = excluded.password,
        phone              = excluded.phone,
        name               = excluded.name,
        role               = excluded.role,
        permissions        = excluded.permissions,
        real_name_verified = excluded.real_name_verified,
        employee_no        = excluded.employee_no;

insert into patient.patient_profile (
    id, phone, name, id_type, id_number,
    gender, birth_date, real_name_verified, verified_at, created_at, updated_at
)
values
    (
        'patient-profile-test-self-001',
        '13800000011',
        '测试患者本人',
        'ID_CARD',
        '110101199003074512',
        'MALE',
        date '1990-03-07',
        true,
        now(),
        now(),
        now()
    ),
    (
        'patient-profile-test-family-001',
        '13800000011',
        '测试患者家属',
        'ID_CARD',
        '110101201505014526',
        'FEMALE',
        date '2015-05-01',
        true,
        now(),
        now(),
        now()
    )
on conflict (id) do update
set phone = excluded.phone,
    name = excluded.name,
    id_type = excluded.id_type,
    id_number = excluded.id_number,
    gender = excluded.gender,
    birth_date = excluded.birth_date,
    real_name_verified = excluded.real_name_verified,
    verified_at = excluded.verified_at,
    updated_at = now();

insert into patient.account_patient_binding (account_id, patient_id, bound_at)
values
    ('patient-test-verified-001', 'patient-profile-test-family-001', now() - interval '1 minute'),
    ('patient-test-verified-001', 'patient-profile-test-self-001', now())
on conflict (account_id, patient_id) do update
set bound_at = excluded.bound_at;

insert into doctor.department (id, name, description, active)
values
    ('dept-rehab', '康复门诊', '康复治疗、针灸、换药及术后随访治疗', true)
on conflict (id) do update
set name = excluded.name,
    description = excluded.description,
    active = excluded.active;

-- 医生档案写入基础表 doctor.doctor
insert into doctor.doctor (id, name, title, department_id, role_type, specialty, employee_no, active)
values
    ('doctor-test-neuro-002',   '李神内医生', '副主任医师', 'dept-neuro',    'OUTPATIENT_DOCTOR', '眩晕、偏头痛、脑血管病随访', '00010010', true),
    ('doctor-test-neuro-003',   '王神内医生', '主治医师',   'dept-neuro',    'OUTPATIENT_DOCTOR', '睡眠障碍、头晕、神经康复随访','00010011', true),
    ('doctor-test-general-002', '赵全科医生', '副主任医师', 'dept-general',  'OUTPATIENT_DOCTOR', '高血压、糖尿病、发热门诊',    '00040010', true),
    ('doctor-test-general-003', '孙全科医生', '主治医师',   'dept-general',  'OUTPATIENT_DOCTOR', '呼吸道感染、慢病随访',        '00040011', true),
    ('doctor-test-rehab-001',   '周康复医生', '主治医师',   'dept-rehab',    'OUTPATIENT_DOCTOR', '针灸、换药、术后康复',        '00050010', true)
on conflict (id) do update
    set name          = excluded.name,
        title         = excluded.title,
        department_id = excluded.department_id,
        role_type     = excluded.role_type,
        specialty     = excluded.specialty,
        employee_no   = excluded.employee_no,
        active        = excluded.active;

-- 补充 dept-rehab 诊室（V8 migration 时该科室尚不存在）
insert into doctor.outpatient_clinic_room (id, department_id, name, location)
select 'room-' || id, id, name || '1号诊室', '门诊楼'
from doctor.department
where id = 'dept-rehab'
on conflict (id) do update
    set name = excluded.name, location = excluded.location;

-- 写入门诊医生扩展表
insert into doctor.outpatient_doctor (doctor_id, clinic_room_id)
values
    ('doctor-test-neuro-002',   'room-dept-neuro'),
    ('doctor-test-neuro-003',   'room-dept-neuro'),
    ('doctor-test-general-002', 'room-dept-general'),
    ('doctor-test-general-003', 'room-dept-general'),
    ('doctor-test-rehab-001',   'room-dept-rehab')
on conflict (doctor_id) do nothing;

insert into doctor.doctor_schedule (
    id, doctor_id, department_id, work_date, period, capacity, status, suspension_reason, updated_at
)
values
    ('schedule-test-general-001', 'doctor-003', 'dept-general', current_date + 1, '下午', 12, 'PUBLISHED', null, now()),
    ('schedule-test-general-002-am', 'doctor-test-general-002', 'dept-general', current_date + 1, '上午', 18, 'PUBLISHED', null, now()),
    ('schedule-test-general-002-pm', 'doctor-test-general-002', 'dept-general', current_date + 3, '下午', 16, 'PUBLISHED', null, now()),
    ('schedule-test-general-003-full', 'doctor-test-general-003', 'dept-general', current_date + 4, '全天', 22, 'PUBLISHED', null, now()),
    ('schedule-test-neuro-001', 'doctor-001', 'dept-neuro', current_date + 3, '上午', 10, 'PUBLISHED', null, now()),
    ('schedule-test-neuro-002-am', 'doctor-test-neuro-002', 'dept-neuro', current_date + 2, '上午', 14, 'PUBLISHED', null, now()),
    ('schedule-test-neuro-002-pm', 'doctor-test-neuro-002', 'dept-neuro', current_date + 2, '下午', 14, 'PUBLISHED', null, now()),
    ('schedule-test-neuro-003-am', 'doctor-test-neuro-003', 'dept-neuro', current_date + 5, '上午', 12, 'PUBLISHED', null, now()),
    ('schedule-test-neuro-history-001', 'doctor-001', 'dept-neuro', current_date - 7, '上午', 10, 'PUBLISHED', null, now()),
    ('schedule-test-rehab-001-am', 'doctor-test-rehab-001', 'dept-rehab', current_date + 2, '上午', 10, 'PUBLISHED', null, now()),
    ('schedule-test-rehab-001-pm', 'doctor-test-rehab-001', 'dept-rehab', current_date + 6, '下午', 8, 'PUBLISHED', null, now())
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
        ('上午', time '08:00'), ('上午', time '08:30'), ('上午', time '09:00'), ('上午', time '09:30'),
        ('下午', time '14:00'), ('下午', time '14:30'), ('下午', time '15:00'), ('下午', time '15:30'),
        ('全天', time '08:00'), ('全天', time '08:30'), ('全天', time '09:00'), ('全天', time '09:30'),
        ('全天', time '14:00'), ('全天', time '14:30'), ('全天', time '15:00'), ('全天', time '15:30')
),
expanded as (
    select s.id as schedule_id, t.start_time, s.capacity,
           count(*) over (partition by s.id) as slot_count,
           row_number() over (partition by s.id order by t.start_time) as slot_index
    from doctor.doctor_schedule s
    join templates t on t.period = s.period
    where s.id like 'schedule-test-%'
       or s.id = 'schedule-002'
)
insert into doctor.doctor_schedule_time_slot (id, schedule_id, start_time, capacity)
select schedule_id || '-' || to_char(start_time, 'HH24MI'),
       schedule_id,
       start_time,
       greatest(1, capacity / slot_count + case when slot_index <= capacity % slot_count then 1 else 0 end)
from expanded
on conflict (schedule_id, start_time) do update
set capacity = excluded.capacity;

delete from appointment.slot_inventory
where schedule_id in ('schedule-001', 'schedule-002', 'schedule-003', 'schedule-004');

insert into appointment.slot_inventory (schedule_id, capacity, locked, booked)
select
    id,
    capacity,
    0,
    case
        when id in ('schedule-002-1400', 'schedule-test-neuro-001-0800', 'schedule-test-rehab-001-am-0800') then 1
        when id = 'schedule-test-general-002-am-0800' then 2
        when id = 'schedule-test-general-002-pm-1400' then 1
        else 0
    end
from doctor.doctor_schedule_time_slot
where schedule_id in (
    'schedule-002',
    'schedule-test-general-001',
    'schedule-test-general-002-am',
    'schedule-test-general-002-pm',
    'schedule-test-general-003-full',
    'schedule-test-neuro-001',
    'schedule-test-neuro-002-am',
    'schedule-test-neuro-002-pm',
    'schedule-test-neuro-003-am',
    'schedule-test-neuro-history-001',
    'schedule-test-rehab-001-am',
    'schedule-test-rehab-001-pm'
)
on conflict (schedule_id) do update
set capacity = excluded.capacity,
    locked = excluded.locked,
    booked = excluded.booked;

insert into appointment.appointment (
    id,
    schedule_id,
    patient_id,
    patient_name,
    doctor_id,
    doctor_name,
    department_id,
    department_name,
    visit_date,
    period,
    start_time,
    source,
    status,
    payment_status,
    payment_method,
    triage_summary,
    risk_level,
    recommended_department_id,
    queue_number,
    missed_count,
    created_at,
    paid_at,
    cancelled_at,
    lock_expires_at,
    business_no
)
values
    (
        'appt-test-future-001',
        'schedule-002-1400',
        'patient-profile-test-self-001',
        '测试患者本人',
        'doctor-001',
        '神内张医生',
        'dept-neuro',
        '神经内科',
        current_date + 1,
        '下午',
        time '14:00',
        'ONLINE',
        'WAITING',
        'PAID',
        'WECHAT_TEST',
        'AI问诊摘要：反复头痛伴轻度头晕，建议门诊随访。',
        'MEDIUM',
        'dept-neuro',
        1,
        0,
        now() - interval '1 day',
        now() - interval '1 day',
        null,
        null,
        'REGQA-FUTURE-001'
    ),
    (
        'appt-test-future-002',
        'schedule-test-neuro-001-0800',
        'patient-profile-test-self-001',
        '测试患者本人',
        'doctor-001',
        '神内张医生',
        'dept-neuro',
        '神经内科',
        current_date + 3,
        '上午',
        time '08:00',
        'ONLINE',
        'WAITING',
        'PAID',
        'WECHAT_TEST',
        'AI问诊摘要：慢性头痛管理复诊。',
        'LOW',
        'dept-neuro',
        1,
        0,
        now(),
        now(),
        null,
        null,
        'REGQA-FUTURE-002'
    ),
    (
        'appt-test-history-001',
        'schedule-test-neuro-history-001-0800',
        'patient-profile-test-self-001',
        '测试患者本人',
        'doctor-001',
        '神内张医生',
        'dept-neuro',
        '神经内科',
        current_date - 7,
        '上午',
        time '08:00',
        'ONLINE',
        'FINISHED',
        'PAID',
        'WECHAT_TEST',
        'AI问诊摘要：头痛发作已缓解。',
        'LOW',
        'dept-neuro',
        1,
        0,
        now() - interval '7 day',
        now() - interval '7 day',
        null,
        null,
        'REGQA-HISTORY-001'
    ),
    (
        'appt-test-pending-payment-001',
        'schedule-test-rehab-001-am-0800',
        'patient-profile-test-self-001',
        '测试患者本人',
        'doctor-test-rehab-001',
        '周康复医生',
        'dept-rehab',
        '康复门诊',
        current_date + 2,
        '上午',
        time '08:00',
        'ONLINE',
        'PENDING_PAYMENT',
        'UNPAID',
        null,
        'AI问诊摘要：颈肩不适，建议换药及康复治疗。',
        'LOW',
        'dept-rehab',
        1,
        0,
        now(),
        null,
        null,
        now() + interval '30 minute',
        'REGQA-PENDING-001'
    )
on conflict (id) do update
set schedule_id = excluded.schedule_id,
    patient_id = excluded.patient_id,
    patient_name = excluded.patient_name,
    doctor_id = excluded.doctor_id,
    doctor_name = excluded.doctor_name,
    department_id = excluded.department_id,
    department_name = excluded.department_name,
    visit_date = excluded.visit_date,
    period = excluded.period,
    start_time = excluded.start_time,
    source = excluded.source,
    status = excluded.status,
    payment_status = excluded.payment_status,
    payment_method = excluded.payment_method,
    triage_summary = excluded.triage_summary,
    risk_level = excluded.risk_level,
    recommended_department_id = excluded.recommended_department_id,
    queue_number = excluded.queue_number,
    missed_count = excluded.missed_count,
    paid_at = excluded.paid_at,
    cancelled_at = excluded.cancelled_at,
    lock_expires_at = excluded.lock_expires_at,
    business_no = excluded.business_no;

insert into cashier.payment_order (
    id, business_type, business_id, patient_id, amount, payment_method, status, operator_id, created_at, paid_at, payment_scene
)
values
    ('pay-test-appt-future-001', 'APPOINTMENT', 'appt-test-future-001', 'patient-profile-test-self-001', 0.01, 'WECHAT_TEST', 'PAID', 'patient-test-verified-001', now() - interval '1 day', now() - interval '1 day', 'BUSINESS'),
    ('pay-test-appt-future-002', 'APPOINTMENT', 'appt-test-future-002', 'patient-profile-test-self-001', 0.01, 'WECHAT_TEST', 'PAID', 'patient-test-verified-001', now(), now(), 'BUSINESS'),
    ('pay-test-appt-history-001', 'APPOINTMENT', 'appt-test-history-001', 'patient-profile-test-self-001', 0.01, 'WECHAT_TEST', 'PAID', 'patient-test-verified-001', now() - interval '7 day', now() - interval '7 day', 'BUSINESS'),
    ('pay-test-appt-pending-001', 'APPOINTMENT', 'appt-test-pending-payment-001', 'patient-profile-test-self-001', 0.01, 'WECHAT_TEST', 'PENDING', 'patient-test-verified-001', now(), null, 'BUSINESS'),
    ('pay-test-order-check-paid-001', 'MEDICAL_ORDER', 'order-test-check-report-001', 'patient-profile-test-self-001', 260.00, 'WECHAT_TEST', 'PAID', 'staff-cashier-001', now() - interval '7 day', now() - interval '7 day', 'BUSINESS'),
    ('pay-test-order-lab-paid-001', 'MEDICAL_ORDER', 'order-test-lab-report-001', 'patient-profile-test-self-001', 35.00, 'WECHAT_TEST', 'PAID', 'staff-cashier-001', now() - interval '7 day', now() - interval '7 day', 'BUSINESS'),
    ('pay-test-order-disposal-paid-001', 'MEDICAL_ORDER', 'order-test-disposal-done-001', 'patient-profile-test-self-001', 25.00, 'WECHAT_TEST', 'PAID', 'staff-cashier-001', now() - interval '7 day', now() - interval '7 day', 'BUSINESS'),
    ('pay-test-order-check-pending-001', 'MEDICAL_ORDER', 'order-test-check-unpaid-001', 'patient-profile-test-self-001', 680.00, 'WECHAT_TEST', 'PENDING', 'patient-test-verified-001', now(), null, 'BUSINESS'),
    ('pay-test-order-lab-pending-001', 'MEDICAL_ORDER', 'order-test-lab-unpaid-001', 'patient-profile-test-self-001', 75.00, 'WECHAT_TEST', 'PENDING', 'patient-test-verified-001', now(), null, 'BUSINESS'),
    ('pay-test-order-disposal-pending-001', 'MEDICAL_ORDER', 'order-test-disposal-unpaid-001', 'patient-profile-test-self-001', 25.00, 'WECHAT_TEST', 'PENDING', 'patient-test-verified-001', now(), null, 'BUSINESS'),
    ('pay-test-rx-dispensed-001', 'PRESCRIPTION', 'rx-test-dispensed-001', 'patient-profile-test-self-001', 47.50, 'WECHAT_TEST', 'PAID', 'staff-cashier-001', now() - interval '7 day', now() - interval '7 day', 'BUSINESS'),
    ('pay-test-rx-pending-001', 'PRESCRIPTION', 'rx-test-pending-001', 'patient-profile-test-self-001', 18.50, 'WECHAT_TEST', 'PENDING', 'patient-test-verified-001', now(), null, 'BUSINESS'),
    ('pay-test-rx-waiting-001', 'PRESCRIPTION', 'rx-test-waiting-001', 'patient-profile-test-self-001', 29.00, 'WECHAT_TEST', 'PAID', 'staff-cashier-001', now() - interval '1 day', now() - interval '1 day', 'BUSINESS')
on conflict (business_type, business_id) do update
set patient_id = excluded.patient_id,
    amount = excluded.amount,
    payment_method = excluded.payment_method,
    status = excluded.status,
    operator_id = excluded.operator_id,
    paid_at = excluded.paid_at,
    payment_scene = excluded.payment_scene;

insert into medical_record.medical_record (
    id,
    appointment_id,
    patient_id,
    patient_name,
    doctor_id,
    doctor_name,
    department_name,
    visit_date,
    period,
    ai_triage_summary,
    ai_risk_level,
    chief_complaint,
    present_illness,
    diagnosis,
    treatment_plan,
    doctor_revision_note,
    status,
    created_at,
    updated_at,
    archived_at,
    past_history,
    allergy_history,
    physical_examination,
    preliminary_diagnosis,
    version,
    diagnosis_created_by_type,
    diagnosis_ai_record_id,
    diagnosis_confirmed_by,
    diagnosis_confirmed_at
)
values
    (
        'record-test-history-001',
        'appt-test-history-001',
        'patient-profile-test-self-001',
        '测试患者本人',
        'doctor-001',
        '神内张医生',
        '神经内科',
        to_char(current_date - 7, 'YYYY-MM-DD'),
        '上午',
        'AI问诊摘要：头痛发作已缓解。',
        'LOW',
        '间断头痛一周。',
        '休息及补液后症状缓解。',
        '原发性头痛，治疗后平稳。',
        '补液、优化睡眠，如症状反复门诊随访。',
        '测试病历数据。',
        'ARCHIVED',
        now() - interval '7 day',
        now() - interval '6 day',
        now() - interval '6 day',
        '既往无重大病史。',
        '无已知药物过敏史。',
        '生命体征平稳，未见局灶性神经功能缺损。',
        '考虑紧张型头痛。',
        1,
        'HUMAN',
        null,
        'doctor-001',
        now() - interval '6 day'
    )
on conflict (appointment_id) do update
set patient_id = excluded.patient_id,
    patient_name = excluded.patient_name,
    doctor_id = excluded.doctor_id,
    doctor_name = excluded.doctor_name,
    department_name = excluded.department_name,
    visit_date = excluded.visit_date,
    period = excluded.period,
    ai_triage_summary = excluded.ai_triage_summary,
    ai_risk_level = excluded.ai_risk_level,
    chief_complaint = excluded.chief_complaint,
    present_illness = excluded.present_illness,
    diagnosis = excluded.diagnosis,
    treatment_plan = excluded.treatment_plan,
    doctor_revision_note = excluded.doctor_revision_note,
    status = excluded.status,
    updated_at = excluded.updated_at,
    archived_at = excluded.archived_at,
    past_history = excluded.past_history,
    allergy_history = excluded.allergy_history,
    physical_examination = excluded.physical_examination,
    preliminary_diagnosis = excluded.preliminary_diagnosis,
    version = excluded.version,
    diagnosis_created_by_type = excluded.diagnosis_created_by_type,
    diagnosis_ai_record_id = excluded.diagnosis_ai_record_id,
    diagnosis_confirmed_by = excluded.diagnosis_confirmed_by,
    diagnosis_confirmed_at = excluded.diagnosis_confirmed_at;

insert into medical_order.medical_order (
    id, appointment_id, patient_id, patient_name, ordering_doctor_id, order_type,
    project_code, project_name, purpose, body_part, amount, payment_status, status,
    executor_id, executor_name, execution_location, equipment_id, queue_number, urgency,
    triage_created_by_type, triage_reasons, missed_count, result_data, result_summary,
    created_at, started_at, completed_at
)
values
    (
        'order-test-check-report-001',
        'appt-test-history-001',
        'patient-profile-test-self-001',
        '测试患者本人',
        'doctor-001',
        'CHECK',
        'CT-HEAD',
        '头颅CT',
        '排查反复头痛原因',
        '头部',
        260.00,
        'PAID',
        'COMPLETED',
        'doctor-check-001',
        '检查医生',
        '影像楼二层CT室',
        'CT-01',
        1,
        'ROUTINE',
        'HUMAN',
        '神经系统影像筛查',
        0,
        '{"finding":"未见急性颅内异常"}'::jsonb,
        '头颅CT未见急性出血及占位性病变。',
        now() - interval '7 day',
        now() - interval '7 day' + interval '1 hour',
        now() - interval '7 day' + interval '2 hour'
    ),
    (
        'order-test-lab-report-001',
        'appt-test-history-001',
        'patient-profile-test-self-001',
        '测试患者本人',
        'doctor-001',
        'LAB',
        'CBC',
        '血常规',
        '筛查炎症指标',
        null,
        35.00,
        'PAID',
        'COMPLETED',
        'doctor-lab-001',
        '检验医生',
        '检验科一层',
        'LAB-01',
        2,
        'ROUTINE',
        'HUMAN',
        '常规血液检查',
        0,
        '{"cbc":"结果大致正常"}'::jsonb,
        '血常规结果大致在正常范围内。',
        now() - interval '7 day',
        now() - interval '7 day' + interval '1 hour',
        now() - interval '7 day' + interval '2 hour'
    ),
    (
        'order-test-disposal-done-001',
        'appt-test-history-001',
        'patient-profile-test-self-001',
        '测试患者本人',
        'doctor-001',
        'DISPOSAL',
        'DISP-INFUSION',
        '补液治疗',
        '对症脱水补液治疗',
        null,
        25.00,
        'PAID',
        'COMPLETED',
        'doctor-disposal-001',
        '处置医生',
        '门诊楼三层处置室',
        null,
        1,
        'ROUTINE',
        'HUMAN',
        '输液观察',
        0,
        '{"response":"输液过程中状态平稳"}'::jsonb,
        '输液顺利完成，未见明显不良反应。',
        now() - interval '7 day',
        now() - interval '7 day' + interval '2 hour',
        now() - interval '7 day' + interval '3 hour'
    ),
    (
        'order-test-disposal-wait-001',
        'appt-test-future-001',
        'patient-profile-test-self-001',
        '测试患者本人',
        'doctor-001',
        'DISPOSAL',
        'DISP-INFUSION',
        '换药处置',
        '伤口复诊换药',
        null,
        25.00,
        'PAID',
        'WAITING',
        'doctor-disposal-001',
        '处置医生',
        '门诊楼三层处置室',
        null,
        3,
        'ROUTINE',
        'HUMAN',
        '门诊接诊后排队处置',
        0,
        null,
        null,
        now() - interval '1 day',
        null,
        null
    ),
    (
        'order-test-check-unpaid-001',
        'appt-test-future-001',
        'patient-profile-test-self-001',
        '测试患者本人',
        'doctor-001',
        'CHECK',
        'MRI-BRAIN',
        '颅脑MRI',
        '进一步评估慢性头痛',
        '颅脑',
        680.00,
        'UNPAID',
        'PENDING_PAYMENT',
        null,
        null,
        null,
        null,
        null,
        'ROUTINE',
        null,
        null,
        0,
        null,
        null,
        now(),
        null,
        null
    ),
    (
        'order-test-lab-unpaid-001',
        'appt-test-future-002',
        'patient-profile-test-self-001',
        '测试患者本人',
        'doctor-001',
        'LAB',
        'LIVER',
        '肝功能组合',
        '用药安全随访',
        null,
        75.00,
        'UNPAID',
        'PENDING_PAYMENT',
        null,
        null,
        null,
        null,
        null,
        'ROUTINE',
        null,
        null,
        0,
        null,
        null,
        now(),
        null,
        null
    ),
    (
        'order-test-disposal-unpaid-001',
        'appt-test-future-002',
        'patient-profile-test-self-001',
        '测试患者本人',
        'doctor-001',
        'DISPOSAL',
        'DISP-INFUSION',
        '输液观察',
        '次日输液随访',
        null,
        25.00,
        'UNPAID',
        'PENDING_PAYMENT',
        null,
        null,
        null,
        null,
        null,
        'ROUTINE',
        null,
        null,
        0,
        null,
        null,
        now(),
        null,
        null
    )
on conflict (id) do update
set appointment_id = excluded.appointment_id,
    patient_id = excluded.patient_id,
    patient_name = excluded.patient_name,
    ordering_doctor_id = excluded.ordering_doctor_id,
    order_type = excluded.order_type,
    project_code = excluded.project_code,
    project_name = excluded.project_name,
    purpose = excluded.purpose,
    body_part = excluded.body_part,
    amount = excluded.amount,
    payment_status = excluded.payment_status,
    status = excluded.status,
    executor_id = excluded.executor_id,
    executor_name = excluded.executor_name,
    execution_location = excluded.execution_location,
    equipment_id = excluded.equipment_id,
    queue_number = excluded.queue_number,
    urgency = excluded.urgency,
    triage_created_by_type = excluded.triage_created_by_type,
    triage_reasons = excluded.triage_reasons,
    missed_count = excluded.missed_count,
    result_data = excluded.result_data,
    result_summary = excluded.result_summary,
    started_at = excluded.started_at,
    completed_at = excluded.completed_at;

update medical_order.medical_order
set executor_workspace_id = executor_id,
    executor_workspace_name = executor_name,
    executor_workspace_location = execution_location
where id in (
    'order-test-check-report-001',
    'order-test-lab-report-001',
    'order-test-disposal-done-001',
    'order-test-disposal-wait-001',
    'order-test-check-unpaid-001',
    'order-test-lab-unpaid-001',
    'order-test-disposal-unpaid-001'
);

insert into medical_order.medical_report (
    id, medical_order_id, report_type, status, findings, conclusion, advice, created_by_type,
    ai_task_id, ai_original_findings, ai_original_conclusion, modified_from_ai,
    confirmed_by, confirmed_at, rejected_by, rejected_at, rejection_reason, created_at, updated_at
)
values
    (
        'report-test-check-001',
        'order-test-check-report-001',
        'CHECK',
        'CONFIRMED',
        '未见急性出血，未见占位性病变，脑室形态可。',
        '头颅CT未见急性颅内异常。',
        '如头痛反复，继续门诊随访。',
        'HUMAN',
        null,
        null,
        null,
        false,
        'doctor-check-001',
        now() - interval '7 day' + interval '2 hour',
        null,
        null,
        null,
        now() - interval '7 day' + interval '1 hour',
        now() - interval '7 day' + interval '2 hour'
    ),
    (
        'report-test-lab-001',
        'order-test-lab-report-001',
        'LAB',
        'CONFIRMED',
        '白细胞、红细胞及血小板计数均在参考范围内。',
        '血常规结果大致在正常范围内。',
        '暂无需紧急检验干预。',
        'HUMAN',
        null,
        null,
        null,
        false,
        'doctor-lab-001',
        now() - interval '7 day' + interval '2 hour',
        null,
        null,
        null,
        now() - interval '7 day' + interval '1 hour',
        now() - interval '7 day' + interval '2 hour'
    )
on conflict (medical_order_id) do update
set report_type = excluded.report_type,
    status = excluded.status,
    findings = excluded.findings,
    conclusion = excluded.conclusion,
    advice = excluded.advice,
    created_by_type = excluded.created_by_type,
    confirmed_by = excluded.confirmed_by,
    confirmed_at = excluded.confirmed_at,
    updated_at = excluded.updated_at;

insert into medical_record.medical_record_report_link (
    id, medical_record_id, medical_order_id, report_id, report_type, conclusion, confirmed_by, confirmed_at
)
values
    (
        '00000000-0000-0000-0000-000000000101',
        'record-test-history-001',
        'order-test-check-report-001',
        'report-test-check-001',
        'CHECK',
        '头颅CT未见急性颅内异常。',
        'doctor-check-001',
        now() - interval '7 day' + interval '2 hour'
    ),
    (
        '00000000-0000-0000-0000-000000000102',
        'record-test-history-001',
        'order-test-lab-report-001',
        'report-test-lab-001',
        'LAB',
        '血常规结果大致在正常范围内。',
        'doctor-lab-001',
        now() - interval '7 day' + interval '2 hour'
    )
on conflict (medical_order_id, report_id) do nothing;

insert into pharmacy.prescription (
    id, prescription_no, appointment_id, medical_record_id, patient_id, patient_name, doctor_id,
    diagnosis, status, total_amount, payment_order_id, ai_assistance_id, ai_adoption_status,
    ai_revision_note, created_at, confirmed_at, paid_at, dispensed_at, returned_at,
    dispensed_by, returned_by, return_reason
)
values
    (
        'rx-test-dispensed-001',
        'RX-QA-001',
        'appt-test-history-001',
        'record-test-history-001',
        'patient-profile-test-self-001',
        '测试患者本人',
        'doctor-001',
        '原发性头痛，治疗后平稳',
        'DISPENSED',
        47.50,
        'pay-test-rx-dispensed-001',
        null,
        'HUMAN_ONLY',
        null,
        now() - interval '7 day',
        now() - interval '7 day',
        now() - interval '7 day',
        now() - interval '6 day',
        null,
        'doctor-pharmacy-001',
        null,
        null
    ),
    (
        'rx-test-pending-001',
        'RX-QA-002',
        'appt-test-future-001',
        null,
        'patient-profile-test-self-001',
        '测试患者本人',
        'doctor-001',
        '头痛随访用药',
        'PENDING_PAYMENT',
        18.50,
        null,
        null,
        'HUMAN_ONLY',
        null,
        now(),
        now(),
        null,
        null,
        null,
        null,
        null,
        null
    ),
    (
        'rx-test-waiting-001',
        'RX-QA-003',
        'appt-test-future-002',
        null,
        'patient-profile-test-self-001',
        '测试患者本人',
        'doctor-001',
        '血脂控制用药',
        'WAITING_DISPENSE',
        29.00,
        'pay-test-rx-waiting-001',
        null,
        'HUMAN_ONLY',
        null,
        now() - interval '1 day',
        now() - interval '1 day',
        now() - interval '1 day',
        null,
        null,
        null,
        null,
        null
    )
on conflict (id) do update
set prescription_no = excluded.prescription_no,
    appointment_id = excluded.appointment_id,
    medical_record_id = excluded.medical_record_id,
    patient_id = excluded.patient_id,
    patient_name = excluded.patient_name,
    doctor_id = excluded.doctor_id,
    diagnosis = excluded.diagnosis,
    status = excluded.status,
    total_amount = excluded.total_amount,
    payment_order_id = excluded.payment_order_id,
    ai_adoption_status = excluded.ai_adoption_status,
    ai_revision_note = excluded.ai_revision_note,
    confirmed_at = excluded.confirmed_at,
    paid_at = excluded.paid_at,
    dispensed_at = excluded.dispensed_at,
    returned_at = excluded.returned_at,
    dispensed_by = excluded.dispensed_by,
    returned_by = excluded.returned_by,
    return_reason = excluded.return_reason;

insert into pharmacy.prescription_item (
    id, prescription_id, drug_id, drug_name, quantity, dosage, usage, frequency, days, note, unit_price, amount
)
values
    ('rx-item-test-001', 'rx-test-dispensed-001', 'drug-aspirin', '阿司匹林肠溶片', 1, '100mg', '口服', '每日一次', 7, '饭后服用', 18.50, 18.50),
    ('rx-item-test-002', 'rx-test-dispensed-001', 'drug-atorvastatin', '阿托伐他汀钙片', 1, '20mg', '口服', '每晚一次', 7, '睡前服用', 29.00, 29.00),
    ('rx-item-test-003', 'rx-test-pending-001', 'drug-aspirin', '阿司匹林肠溶片', 1, '100mg', '口服', '每日一次', 7, '复诊后继续使用', 18.50, 18.50),
    ('rx-item-test-004', 'rx-test-waiting-001', 'drug-atorvastatin', '阿托伐他汀钙片', 1, '20mg', '口服', '每晚一次', 7, '长期稳定用药', 29.00, 29.00)
on conflict (id) do update
set prescription_id = excluded.prescription_id,
    drug_id = excluded.drug_id,
    drug_name = excluded.drug_name,
    quantity = excluded.quantity,
    dosage = excluded.dosage,
    usage = excluded.usage,
    frequency = excluded.frequency,
    days = excluded.days,
    note = excluded.note,
    unit_price = excluded.unit_price,
    amount = excluded.amount;

commit;
