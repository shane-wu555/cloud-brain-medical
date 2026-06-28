package com.cloudbrain.medicalrecord;

import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 开发环境：为 DevQueueSeeder 插入的测试患者补种初始病历（含 AI 摘要和初步诊断预填）。
 */
@Component
public class DevRecordSeeder implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    public DevRecordSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        Integer count = jdbc.queryForObject(
                "select count(*) from medical_record where appointment_id like 'appt-dev-%'",
                Integer.class);
        if (count != null && count > 0) return;

        record R(String id, String apptId, String patId, String name,
                 String triage, String risk,
                 String cc, String pi, String ph, String ah, String pd, String tp) {}

        List<R> rows = List.of(
            new R("mr-dev-001","appt-dev-001","pat-dev-001","刘建国",
                "反复头晕3天，血压偏高（150/95mmHg），既往高血压病史5年","MEDIUM",
                "反复头晕3天",
                "患者3天前无明显诱因出现头晕，伴视物旋转，与体位改变有关，血压150/95mmHg。无耳鸣，无恶心呕吐。",
                "高血压病史5年，规律服药","无",
                "1.良性位置性眩晕 2.高血压病3级",
                "予耳石复位治疗；调整降压方案，加用氨氯地平5mg qd"),
            new R("mr-dev-002","appt-dev-002","pat-dev-002","张秀英",
                "失眠2周，入睡困难，伴焦虑情绪，日间头痛","LOW",
                "失眠2周",
                "2周前因工作压力出现失眠，入睡困难，睡眠浅，每晚约3-4小时，伴焦虑、情绪低落，日间头痛明显。",
                "无特殊","无",
                "1.失眠症 2.焦虑状态",
                "认知行为治疗；艾司唑仑0.5mg qn短期使用；必要时转诊心理科"),
            new R("mr-dev-003","appt-dev-003","pat-dev-003","王大力",
                "持续头痛3天，颈部酸痛，伴恶心，无发热","MEDIUM",
                "头痛3天",
                "头痛呈持续性钝痛，以双侧颞部及枕部为主，颈部肌肉紧张，长期伏案工作，伴轻度恶心，无呕吐，无发热。",
                "无特殊","青霉素过敏",
                "1.紧张型头痛 2.颈椎病",
                "布洛芬缓释胶囊0.3g bid；甲钴胺0.5mg tid；颈部理疗；嘱改善坐姿"),
            new R("mr-dev-004","appt-dev-004","pat-dev-004","陈小梅",
                "脑梗死后随访，左侧肢体活动较前好转","HIGH",
                "脑梗死后随访",
                "患者2个月前因急性脑梗死住院，目前规律服用阿司匹林100mg qd、阿托伐他汀20mg qn，左侧上肢肌力4级，步态稳。血压130/80mmHg。",
                "高血压、糖尿病","无",
                "脑梗死恢复期",
                "继续原方案；加强康复锻炼；3个月后复查头颅MRI及血脂"),
            new R("mr-dev-005","appt-dev-005","pat-dev-005","李明远",
                "发作性眩晕2天，视物模糊，站立不稳","HIGH",
                "发作性眩晕2天",
                "患者2天前突发眩晕，伴视物模糊、站立不稳，无耳鸣，步态不稳，休息后略缓解。Romberg征(+)。",
                "无特殊","无",
                "后循环缺血待排",
                "急查头颅MRI+MRA；倍他司汀6mg tid；卧床休息")
        );

        String sql = """
            insert into medical_record (
              id, appointment_id, patient_id, patient_name,
              doctor_id, doctor_name, department_name, visit_date, period,
              ai_triage_summary, ai_risk_level,
              chief_complaint, present_illness, past_history, allergy_history,
              physical_examination, preliminary_diagnosis, diagnosis, treatment_plan,
              doctor_revision_note, status, version, created_at, updated_at
            ) values (
              ?, ?, ?, ?,
              'doctor-001', '张医生', '神经内科', current_date, '上午',
              ?, ?,
              ?, ?, ?, ?,
              '', ?, '', ?,
              '', 'DRAFT', 0, now(), now()
            ) on conflict (id) do nothing
            """;

        for (R r : rows) {
            jdbc.update(sql,
                r.id(), r.apptId(), r.patId(), r.name(),
                r.triage(), r.risk(),
                r.cc(), r.pi(), r.ph(), r.ah(),
                r.pd(), r.tp());
        }

        System.out.println("[DevRecordSeeder] 已补种 " + rows.size() + " 份初始病历");
    }
}
