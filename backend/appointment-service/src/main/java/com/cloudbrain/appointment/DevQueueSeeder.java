package com.cloudbrain.appointment;

import java.time.LocalTime;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 开发环境数据补种：每次启动时将 dev 测试患者的 visit_date 刷新为当天，
 * 确保每日重启后队列始终有数据，不受日期变化影响。
 */
@Component
public class DevQueueSeeder implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    public DevQueueSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        refreshDevQueue();
        refreshCtValidationQueue();
    }

    private void refreshDevQueue() {
        // 修复历史遗留的非法 SKIPPED 状态（SKIPPED 已从业务逻辑中移除）
        jdbc.update("update appointment set status='WAITING' where id like 'appt-dev-%' and status='SKIPPED'");

        Integer fresh = jdbc.queryForObject(
                "select count(*) from appointment where id like 'appt-dev-%' and visit_date = current_date",
                Integer.class);
        if (fresh != null && fresh > 0) return;

        jdbc.update("""
                insert into slot_inventory (schedule_id, capacity, locked, booked)
                values (?, ?, ?, ?)
                on conflict do nothing
                """, "schedule-dev-neuro", 30, 0, 0);

        record P(String id, String patId, String name, String status,
                 String triage, String risk, int q, String start) {}

        // 队列号从 100 开始，避免与真实患者（通常 1-99）的唯一约束冲突
        List<P> patients = List.of(
            new P("appt-dev-001", "pat-dev-001", "刘建国", "WAITING",
                "反复头晕3天，血压偏高（150/95mmHg），既往高血压病史5年", "MEDIUM", 100, "08:00"),
            new P("appt-dev-002", "pat-dev-002", "张秀英", "WAITING",
                "失眠2周，入睡困难，伴焦虑情绪，日间头痛", "LOW", 101, "08:15"),
            new P("appt-dev-003", "pat-dev-003", "王大力", "WAITING",
                "持续头痛3天，颈部酸痛，伴恶心，无发热", "MEDIUM", 102, "08:30"),
            new P("appt-dev-004", "pat-dev-004", "陈小梅", "WAITING",
                "脑梗死后随访，左侧肢体活动较前好转，服用阿司匹林+他汀", "HIGH", 103, "09:00"),
            new P("appt-dev-005", "pat-dev-005", "李明远", "CALLED",
                "发作性眩晕2天，视物模糊，站立不稳，排除前庭疾患", "HIGH", 104, "09:30"),
            new P("appt-dev-006", "pat-dev-006", "赵晓燕", "WAITING",
                "偏头痛急性发作，视觉先兆（闪光暗点），持续约2小时", "MEDIUM", 105, "10:00"),
            new P("appt-dev-007", "pat-dev-007", "孙志强", "WAITING",
                "帕金森病随访，服用左旋多巴，近期出现轻微幻觉", "HIGH", 106, "10:30"),
            new P("appt-dev-008", "pat-dev-008", "周芳芳", "REVISIT_WAITING",
                "已完成头颅MRI，携带报告复诊，初诊考虑后循环缺血", "HIGH", 107, "11:00"),
            new P("appt-dev-009", "pat-dev-009", "吴德强", "FINISHED",
                "三叉神经痛复诊，卡马西平加量后疼痛明显减轻", "LOW", 108, "11:30"),
            new P("appt-dev-010", "pat-dev-010", "郑玉兰", "FINISHED",
                "癫痫随访，末次发作6个月前，丙戊酸钠血药浓度达标", "MEDIUM", 109, "11:45"),
            new P("appt-dev-011", "pat-dev-011", "林小华", "WAITING",
                "突发剧烈头痛，呕吐1次，无发热，需排除蛛网膜下腔出血", "HIGH", 110, "10:00"),
            new P("appt-dev-012", "pat-dev-012", "黄建平", "WAITING",
                "双下肢无力2周，行走不稳，排除脊髓病变", "HIGH", 111, "10:15"),
            new P("appt-dev-013", "pat-dev-013", "陈晓丽", "WAITING",
                "面部麻木伴口角歪斜3小时，急诊就诊，初诊怀疑TIA", "HIGH", 112, "10:30"),
            new P("appt-dev-014", "pat-dev-014", "周明辉", "WAITING",
                "记忆力下降6个月，认知功能减退，家属陪同就诊", "MEDIUM", 113, "11:00"),
            new P("appt-dev-015", "pat-dev-015", "吴晓红", "WAITING",
                "紧张型头痛，长期颈肩酸痛，情绪焦虑，工作压力大", "LOW", 114, "09:00")
        );

        for (P p : patients) {
            jdbc.update("""
                    insert into appointment (
                      id, schedule_id, patient_id, patient_name,
                      doctor_id, doctor_name, department_id, department_name,
                      visit_date, period, start_time,
                      source, status, payment_status, payment_method,
                      triage_summary, risk_level, queue_number, missed_count, paid_at
                    ) values (?,?,?,?,
                      'doctor-001','张医生','dept-neuro','神经内科',
                      current_date,'上午',?,
                      'OFFLINE',?,'PAID','OFFLINE_WINDOW',
                      ?,?,?,0,now())
                    on conflict (id) do update
                      set visit_date    = current_date,
                          status        = excluded.status,
                          queue_number  = excluded.queue_number,
                          triage_summary = excluded.triage_summary,
                          risk_level    = excluded.risk_level
                    """,
                    p.id(), "schedule-dev-neuro", p.patId(), p.name(),
                    LocalTime.parse(p.start()), p.status(),
                    p.triage(), p.risk(), p.q());
        }

        System.out.println("[DevQueueSeeder] 已刷新张医生今日候诊队列（15人）");
    }

    /** 将 V11 migration 写入的头部CT验证患者日期刷新为今天 */
    private void refreshCtValidationQueue() {
        Integer fresh = jdbc.queryForObject(
                "select count(*) from appointment where id like 'appt-ct-%' and visit_date = current_date",
                Integer.class);
        if (fresh != null && fresh >= 15) return;

        int updated = jdbc.update("""
                update appointment
                set visit_date = current_date,
                    status     = case when status = 'FINISHED' then status else 'WAITING' end
                where id like 'appt-ct-%'
                """);
        if (updated > 0) {
            System.out.printf("[DevQueueSeeder] 已将 %d 名头部CT验证患者刷新至今日%n", updated);
        }
    }
}
