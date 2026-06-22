package com.cloudbrain.doctor.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DoctorCatalogRepository {
    private final JdbcTemplate jdbc;

    public DoctorCatalogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Department> departments() {
        return jdbc.query(
                "select id, name, description from department where active order by name",
                (rs, rowNum) -> new Department(rs.getString(1), rs.getString(2), rs.getString(3)));
    }

    public List<Doctor> doctors(String departmentId) {
        return jdbc.query("""
                select d.id, d.name, d.title, d.department_id, department.name, d.specialty, d.role_type
                from doctor d
                join department on department.id = d.department_id
                where d.active and (? is null or d.department_id = ?)
                order by d.name
                """, (rs, rowNum) -> new Doctor(
                rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7)), departmentId, departmentId);
    }

    public List<Schedule> schedules(String doctorId, String departmentId) {
        return jdbc.query("""
                select schedule.id, schedule.doctor_id, doctor.name, schedule.department_id,
                       schedule.work_date, schedule.period, schedule.capacity
                from doctor_schedule schedule
                join doctor on doctor.id = schedule.doctor_id
                where schedule.status = 'PUBLISHED'
                  and (? is null or schedule.doctor_id = ?)
                  and (? is null or schedule.department_id = ?)
                order by schedule.work_date, schedule.period
                """, (rs, rowNum) -> new Schedule(
                rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getDate(5).toLocalDate(), rs.getString(6), rs.getInt(7)),
                doctorId, doctorId, departmentId, departmentId);
    }

    public Schedule createSchedule(String doctorId, String departmentId, LocalDate date, String period, int capacity) {
        String id = "schedule-" + UUID.randomUUID();
        jdbc.update("""
                insert into doctor_schedule (id, doctor_id, department_id, work_date, period, capacity)
                values (?, ?, ?, ?, ?, ?)
                """, id, doctorId, departmentId, date, period, capacity);
        return schedules(doctorId, departmentId).stream()
                .filter(schedule -> schedule.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    public record Department(String id, String name, String description) {}
    public record Doctor(
            String id, String name, String title, String departmentId,
            String departmentName, String specialty, String roleType) {}
    public record Schedule(
            String id, String doctorId, String doctorName, String departmentId,
            LocalDate workDate, String period, int capacity) {}
}
