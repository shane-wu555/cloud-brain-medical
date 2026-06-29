package com.cloudbrain.medicalrecord;

import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 开发环境：为 DevQueueSeeder 插入的测试患者补种初始病历。
 * 使用新 UUID 格式的 appointment_id 和 patient_id（对齐 appointment V2 迁移）。
 */
@Component
public class DevRecordSeeder implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    public DevRecordSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        // 按医生工号 + 今日日期判断是否已存在，无需 LIKE 匹配 uuid
        Integer count = jdbc.queryForObject(
                "select count(*) from medical_record where doctor_id = '00010001' and visit_date = current_date",
                Integer.class);
        if (count != null && count > 0) return;

        record R(String apptId, String patId, String name,
                 String triage, String risk,
                 String cc, String pi, String ph, String ah, String pd, String tp) {}

        // appointment_id / patient_id 与 appointment V2 + patient V2 一致
        List<R> rows = List.of(
            new R("00000000-0000-4000-8000-000000000001",
                  "a0000000-0000-4000-8000-000000000001","刘建国",
                "反复头晕3天，血压偏高（150/95mmHg），既往高血压病史5年","MEDIUM",
                "反复头晕3天","患者3天前无明显诱因出现头晕，伴视物旋转，血压150/95mmHg。",
                "高血压病史5年，规律服药","无","1.良性位置性眩晕 2.高血压病3级",
                "耳石复位治疗；调整降压方案加用氨氯地平5mg qd"),
            new R("00000000-0000-4000-8000-000000000002",
                  "a0000000-0000-4000-8000-000000000002","张秀英",
                "失眠2周，入睡困难，伴焦虑情绪，日间头痛","LOW",
                "失眠2周","2周前因工作压力出现失眠，每晚约3-4小时，伴焦虑、情绪低落。",
                "无特殊","无","1.失眠症 2.焦虑状态",
                "认知行为治疗；艾司唑仑0.5mg qn短期使用"),
            new R("00000000-0000-4000-8000-000000000003",
                  "a0000000-0000-4000-8000-000000000003","王大力",
                "持续头痛3天，颈部酸痛，伴恶心，无发热","MEDIUM",
                "头痛3天","头痛呈持续性钝痛，颈部肌肉紧张，伴轻度恶心，无呕吐。",
                "无特殊","青霉素过敏","1.紧张型头痛 2.颈椎病",
                "布洛芬缓释0.3g bid；甲钴胺0.5mg tid；颈部理疗"),
            new R("00000000-0000-4000-8000-000000000004",
                  "a0000000-0000-4000-8000-000000000004","陈小梅",
                "脑梗死后随访，左侧肢体活动较前好转","HIGH",
                "脑梗死后随访","阿司匹林+他汀规律服用，左侧上肢肌力4级，血压130/80mmHg。",
                "高血压、糖尿病","无","脑梗死恢复期",
                "继续原方案；加强康复锻炼；3个月后复查MRI及血脂"),
            new R("00000000-0000-4000-8000-000000000005",
                  "a0000000-0000-4000-8000-000000000005","李明远",
                "发作性眩晕2天，视物模糊，站立不稳","HIGH",
                "发作性眩晕2天","突发眩晕，伴视物模糊、站立不稳，Romberg征(+)。",
                "无特殊","无","后循环缺血待排",
                "急查头颅MRI+MRA；倍他司汀6mg tid；卧床休息")
        );

        // 新 schema：无 period / ai_risk_level 列；id 由 DB 自动生成（uuid）
        String sql = """
            insert into medical_record (
              id, appointment_id, patient_id, patient_name,
              doctor_id, doctor_name, department_name, visit_date,
              ai_triage_summary,
              chief_complaint, present_illness, past_history, allergy_history,
              preliminary_diagnosis, treatment_plan,
              status, version, created_at, updated_at
            ) values (
              'record-' || gen_random_uuid(), ?::uuid, ?::uuid, ?,
              '00010001', '张医生', '神经内科', current_date,
              ?,
              ?, ?, ?, ?,
              ?, ?,
              'DRAFT', 0, now(), now()
            ) on conflict (appointment_id) do nothing
            """;

        int inserted = 0;
        for (R r : rows) {
            inserted += jdbc.update(sql,
                r.apptId(), r.patId(), r.name(),
                r.triage(),
                r.cc(), r.pi(), r.ph(), r.ah(),
                r.pd(), r.tp());
        }

        if (inserted > 0) {
            System.out.println("[DevRecordSeeder] 已补种 " + inserted + " 份初始病历");
        }
    }
}
