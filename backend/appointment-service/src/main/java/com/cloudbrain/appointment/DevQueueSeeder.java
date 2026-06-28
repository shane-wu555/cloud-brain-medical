package com.cloudbrain.appointment;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 开发环境数据补种：每次启动时检查张医生（doctor-001）今日是否有候诊数据，
 * 若无则自动插入 10 条测试患者记录，方便前端调试。
 */
@Component
public class DevQueueSeeder implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    public DevQueueSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        Integer count = jdbc.queryForObject(
                "select count(*) from appointment where doctor_id='doctor-001' and visit_date=current_date",
                Integer.class);
        if (count != null && count > 0) return;

        jdbc.update("insert into slot_inventory (schedule_id, capacity, locked, booked) values (?,?,?,?) on conflict do nothing",
                "schedule-today-neuro-dev", 30, 0, 10);

        record P(String id, String patId, String name, String status, String triage, String risk, int q, String start) {}

        List<P> patients = List.of(
            new P("appt-dev-001","pat-dev-001","刘建国","WAITING",
                "反复头晕3天，血压偏高（150/95mmHg），既往高血压病史5年","MEDIUM",1,"08:00"),
            new P("appt-dev-002","pat-dev-002","张秀英","WAITING",
                "失眠2周，入睡困难，伴焦虑情绪，日间头痛","LOW",2,"08:15"),
            new P("appt-dev-003","pat-dev-003","王大力","WAITING",
                "持续头痛3天，颈部酸痛，伴恶心，无发热","MEDIUM",3,"08:30"),
            new P("appt-dev-004","pat-dev-004","陈小梅","WAITING",
                "脑梗死后随访，左侧肢体活动较前好转，服用阿司匹林+他汀","HIGH",4,"09:00"),
            new P("appt-dev-005","pat-dev-005","李明远","CALLED",
                "发作性眩晕2天，视物模糊，站立不稳，排除前庭疾患","HIGH",5,"09:30"),
            new P("appt-dev-006","pat-dev-006","赵晓燕","WAITING",
                "偏头痛急性发作，视觉先兆（闪光暗点），持续约2小时","MEDIUM",6,"10:00"),
            new P("appt-dev-007","pat-dev-007","孙志强","WAITING",
                "帕金森病随访，服用左旋多巴，近期出现轻微幻觉","HIGH",7,"10:30"),
            new P("appt-dev-008","pat-dev-008","周芳芳","REVISIT_WAITING",
                "已完成头颅MRI，携带报告复诊，初诊考虑后循环缺血","HIGH",8,"11:00"),
            new P("appt-dev-009","pat-dev-009","吴德强","FINISHED",
                "三叉神经痛复诊，卡马西平加量后疼痛明显减轻","LOW",9,"08:00"),
            new P("appt-dev-010","pat-dev-010","郑玉兰","FINISHED",
                "癫痫随访，末次发作6个月前，丙戊酸钠血药浓度达标","MEDIUM",10,"08:15")
        );

        for (P p : patients) {
            jdbc.update("""
                insert into appointment (
                  id, schedule_id, patient_id, patient_name, doctor_id, doctor_name,
                  department_id, department_name, visit_date, period, start_time,
                  source, status, payment_status, payment_method,
                  triage_summary, risk_level, queue_number, missed_count, paid_at
                ) values (?,?,?,?,'doctor-001','张医生','dept-neuro','神经内科',
                  current_date,'上午',?,
                  'OFFLINE',?,'PAID','OFFLINE_WINDOW',?,?,?,0,now())
                on conflict (id) do nothing
                """,
                p.id(), "schedule-today-neuro-dev", p.patId(), p.name(),
                LocalTime.parse(p.start()), p.status(), p.triage(), p.risk(), p.q());
        }

        System.out.println("[DevQueueSeeder] 已补种张医生今日候诊队列（10人）");
    }
}
