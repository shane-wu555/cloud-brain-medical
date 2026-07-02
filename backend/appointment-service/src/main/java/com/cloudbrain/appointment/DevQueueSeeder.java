package com.cloudbrain.appointment;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 开发环境数据补种：每次启动时将测试队列的 visit_date 刷新为今天。
 *
 * appointment.id 为 uuid 类型，不能用 LIKE 匹配；
 * 改为按 doctor_id + queue_number 范围过滤。
 */
@Component
public class DevQueueSeeder implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    public DevQueueSeeder(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(String... args) {
        refreshDevQueue();
        refreshCtValidationQueue();
    }

    private void refreshDevQueue() {
        try {
            jdbc.update("update doctor.schedule set work_date = current_date where id = 'sched-00010001-am'");
        } catch (Exception ignored) {}

        int updated = jdbc.update("""
                update appointment
                set visit_date = current_date
                where doctor_id = '00010001'
                  and queue_number between 100 and 114
                """);
        if (updated > 0) System.out.println("[DevQueueSeeder] 刷新张医生今日神经内科队列：" + updated + " 人");
    }

    private void refreshCtValidationQueue() {
        try {
            jdbc.update("update doctor.schedule set work_date = current_date where id = 'sched-ct-valid'");
        } catch (Exception ignored) {}

        int updated = jdbc.update("""
                update appointment
                set visit_date = current_date
                where doctor_id = '00010001'
                  and queue_number between 200 and 214
                """);
        if (updated > 0) System.out.println("[DevQueueSeeder] 刷新CT验证下午队列：" + updated + " 人");
    }
}
