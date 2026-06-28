-- ══════════════════════════════════════════════════════════════════
-- V11: 头部CT验证测试患者
-- 为张医生（doctor-001）神经内科今日队列插入15名头部相关患者，
-- 覆盖外伤、占位、血管病、感染等典型CT适应症。
-- 队列号 200-214，与dev数据(100-114)及真实数据隔离。
-- DevQueueSeeder 会在每次启动时将 visit_date 刷新为 current_date。
-- ══════════════════════════════════════════════════════════════════

insert into slot_inventory (schedule_id, capacity, locked, booked)
values ('schedule-ct-validation', 30, 0, 0)
on conflict (schedule_id) do nothing;

insert into appointment (
    id, schedule_id, patient_id, patient_name,
    doctor_id, doctor_name, department_id, department_name,
    visit_date, period, start_time,
    source, status, payment_status, payment_method,
    triage_summary, risk_level, queue_number, missed_count, paid_at
) values

-- ── 急诊优先（EMERGENCY / HIGH）──
('appt-ct-001', 'schedule-ct-validation', 'pat-ct-001', '李建军',
 'doctor-001', '张医生', 'dept-neuro', '神经内科',
 current_date, '上午', '08:00',
 'OFFLINE', 'WAITING', 'PAID', 'OFFLINE_WINDOW',
 '头部外伤后头痛2小时，GCS评分14分，右额颞部压痛明显，需排除颅内出血及颅骨骨折', 'HIGH',
 200, 0, now()),

('appt-ct-002', 'schedule-ct-validation', 'pat-ct-002', '周红英',
 'doctor-001', '张医生', 'dept-neuro', '神经内科',
 current_date, '上午', '08:10',
 'OFFLINE', 'WAITING', 'PAID', 'OFFLINE_WINDOW',
 '车祸伤后意识短暂丧失约5分钟，醒后头痛恶心，疑闭合性颅脑损伤', 'HIGH',
 201, 0, now()),

('appt-ct-003', 'schedule-ct-validation', 'pat-ct-003', '张永军',
 'doctor-001', '张医生', 'dept-neuro', '神经内科',
 current_date, '上午', '08:20',
 'ONLINE', 'WAITING', 'PAID', 'WECHAT',
 '突发"霹雳样"头痛，疼痛评分9/10，伴颈项强直，疑蛛网膜下腔出血', 'HIGH',
 202, 0, now()),

('appt-ct-004', 'schedule-ct-validation', 'pat-ct-004', '朱晓燕',
 'doctor-001', '张医生', 'dept-neuro', '神经内科',
 current_date, '上午', '08:30',
 'ONLINE', 'WAITING', 'PAID', 'ALIPAY',
 '持续头痛伴喷射性呕吐，视盘水肿，颅内高压体征明显，需CT评估占位可能', 'HIGH',
 203, 0, now()),

('appt-ct-005', 'schedule-ct-validation', 'pat-ct-005', '陈志强',
 'doctor-001', '张医生', 'dept-neuro', '神经内科',
 current_date, '上午', '08:40',
 'OFFLINE', 'WAITING', 'PAID', 'OFFLINE_WINDOW',
 '首次癫痫发作，强直-阵挛，持续约2分钟，发作后头痛，CT排除继发性原因', 'HIGH',
 204, 0, now()),

-- ── 常规检查（MEDIUM）──
('appt-ct-006', 'schedule-ct-validation', 'pat-ct-006', '王丽华',
 'doctor-001', '张医生', 'dept-neuro', '神经内科',
 current_date, '上午', '09:00',
 'ONLINE', 'WAITING', 'PAID', 'WECHAT',
 '持续性头痛3周，夜间加重，布洛芬无效，需排除颅内占位及脑膜炎', 'MEDIUM',
 205, 0, now()),

('appt-ct-007', 'schedule-ct-validation', 'pat-ct-007', '孙建国',
 'doctor-001', '张医生', 'dept-neuro', '神经内科',
 current_date, '上午', '09:15',
 'ONLINE', 'WAITING', 'PAID', 'WECHAT',
 '脑梗死后1个月随访，左侧肢体肌力较前略下降，CT评估梗死灶演变及侧支循环', 'MEDIUM',
 206, 0, now()),

('appt-ct-008', 'schedule-ct-validation', 'pat-ct-008', '刘芳',
 'doctor-001', '张医生', 'dept-neuro', '神经内科',
 current_date, '上午', '09:30',
 'ONLINE', 'WAITING', 'PAID', 'ALIPAY',
 '头晕伴视物模糊1周，血压130/85，排除后颅窝病变及颈动脉斑块相关缺血', 'MEDIUM',
 207, 0, now()),

('appt-ct-009', 'schedule-ct-validation', 'pat-ct-009', '钱大明',
 'doctor-001', '张医生', 'dept-neuro', '神经内科',
 current_date, '上午', '09:45',
 'OFFLINE', 'WAITING', 'PAID', 'OFFLINE_WINDOW',
 '高血压患者（180/110mmHg）伴突发头痛，排除高血压脑病及颅内出血', 'MEDIUM',
 208, 0, now()),

('appt-ct-010', 'schedule-ct-validation', 'pat-ct-010', '赵小燕',
 'doctor-001', '张医生', 'dept-neuro', '神经内科',
 current_date, '上午', '10:00',
 'ONLINE', 'WAITING', 'PAID', 'WECHAT',
 '偏头痛病史5年，近2个月发作频率明显增加，先兆症状异常，需排除器质性改变', 'MEDIUM',
 209, 0, now()),

('appt-ct-011', 'schedule-ct-validation', 'pat-ct-011', '吴小红',
 'doctor-001', '张医生', 'dept-neuro', '神经内科',
 current_date, '上午', '10:15',
 'OFFLINE', 'WAITING', 'PAID', 'OFFLINE_WINDOW',
 '右颞部头皮肿块，直径约2cm，质硬，排除颅骨病变及硬膜外受累', 'MEDIUM',
 210, 0, now()),

('appt-ct-012', 'schedule-ct-validation', 'pat-ct-012', '林美华',
 'doctor-001', '张医生', 'dept-neuro', '神经内科',
 current_date, '上午', '10:30',
 'ONLINE', 'WAITING', 'PAID', 'WECHAT',
 '老年痴呆症状3个月内明显加重，排除正常颅压脑积水及慢性硬膜下血肿', 'MEDIUM',
 211, 0, now()),

('appt-ct-013', 'schedule-ct-validation', 'pat-ct-013', '徐志明',
 'doctor-001', '张医生', 'dept-neuro', '神经内科',
 current_date, '上午', '10:45',
 'ONLINE', 'WAITING', 'PAID', 'ALIPAY',
 '记忆力减退6个月，近事记忆差，MoCA评分22分，CT评估海马萎缩与脑白质病变', 'MEDIUM',
 212, 0, now()),

-- ── 低风险常规（LOW）──
('appt-ct-014', 'schedule-ct-validation', 'pat-ct-014', '黄建国',
 'doctor-001', '张医生', 'dept-neuro', '神经内科',
 current_date, '上午', '11:00',
 'OFFLINE', 'WAITING', 'PAID', 'OFFLINE_WINDOW',
 '颈肩部酸痛伴间歇性头痛，低头时明显，排除颈椎病相关颅内病变', 'LOW',
 213, 0, now()),

('appt-ct-015', 'schedule-ct-validation', 'pat-ct-015', '郑建华',
 'doctor-001', '张医生', 'dept-neuro', '神经内科',
 current_date, '上午', '11:15',
 'ONLINE', 'WAITING', 'PAID', 'WECHAT',
 '反复发作性眩晕，与体位无关，Dix-Hallpike阴性，需中枢性眩晕影像学评估', 'LOW',
 214, 0, now())

on conflict (id) do update
    set visit_date    = current_date,
        status        = excluded.status,
        queue_number  = excluded.queue_number,
        triage_summary = excluded.triage_summary,
        risk_level    = excluded.risk_level;
