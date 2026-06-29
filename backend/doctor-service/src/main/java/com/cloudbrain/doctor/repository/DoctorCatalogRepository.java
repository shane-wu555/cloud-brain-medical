package com.cloudbrain.doctor.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 适配新 doctor schema：
 *   doctor                  → staff（doctor_id → staff_id）
 *   doctor_schedule         → schedule（doctor_id → staff_id，无 updated_at）
 *   doctor_schedule_time_slot → schedule_slot
 *   outpatient_clinic_room  → outpatient_room
 *   outpatient_doctor.doctor_id → staff_id
 *   outpatient_doctor.clinic_room_id → room_id
 */
@Repository
public class DoctorCatalogRepository {
    private final JdbcTemplate jdbc;
    public DoctorCatalogRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // ── 科室 ───────────────────────────────────────────────────────────
    public List<Department> departments() {
        return jdbc.query("select id,name,description from department where active order by name",
                (rs, row) -> new Department(rs.getString(1), rs.getString(2), rs.getString(3)));
    }
    public Department createDepartment(String name, String description) {
        String id = "dept-" + UUID.randomUUID();
        jdbc.update("insert into department (id,name,description) values (?,?,?)", id, name, description);
        return new Department(id, name, description);
    }

    // ── 医生（staff 表，仅门诊医生）────────────────────────────────────
    public List<Doctor> doctors(String departmentId) {
        StringBuilder sql = new StringBuilder("""
                select s.id, s.employee_no, s.name, s.title, s.department_id, p.name as dept_name,
                       s.specialty, s.role_type
                from staff s
                join department p on p.id = s.department_id
                where s.active and s.role_type = 'OUTPATIENT_DOCTOR'
                """);
        List<Object> args = new ArrayList<>();
        if (departmentId != null && !departmentId.isBlank()) {
            sql.append(" and s.department_id = ?");
            args.add(departmentId);
        }
        sql.append(" order by s.name");
        return jdbc.query(sql.toString(), (rs, row) -> new Doctor(
                rs.getString("id"), rs.getString("employee_no"), rs.getString("name"),
                rs.getString("title"), rs.getString("department_id"), rs.getString("dept_name"),
                rs.getString("specialty"), rs.getString("role_type")), args.toArray());
    }

    public Doctor createDoctor(String employeeNo, String name, String title,
            String departmentId, String roleType, String specialty) {
        String id = UUID.randomUUID().toString();
        jdbc.update("insert into staff (id,employee_no,name,title,department_id,role_type,specialty) values (?,?,?,?,?,?,?)",
                id, employeeNo, name, title, departmentId, roleType, specialty);
        jdbc.update("""
                insert into outpatient_room (id,department_id,name,location)
                select ?,id,name || ' Default Room','Outpatient Building'
                from department where id=?
                on conflict (id) do nothing
                """, "room-" + departmentId, departmentId);
        jdbc.update("""
                insert into outpatient_doctor (staff_id, room_id)
                values (?,?)
                on conflict (staff_id) do update set room_id = excluded.room_id
                """, id, "room-" + departmentId);
        return doctors(departmentId).stream().filter(d -> d.id().equals(id)).findFirst().orElseThrow();
    }

    // ── 排班 ───────────────────────────────────────────────────────────
    public List<Schedule> schedules(String doctorId, String departmentId) {
        StringBuilder sql = new StringBuilder("""
                select s.id, s.staff_id, d.name as doctor_name, s.department_id,
                       s.work_date, s.period, s.capacity, s.status
                from schedule s join staff d on d.id = s.staff_id
                where 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (doctorId != null && !doctorId.isBlank()) { sql.append(" and s.staff_id = ?"); args.add(doctorId); }
        if (departmentId != null && !departmentId.isBlank()) { sql.append(" and s.department_id = ?"); args.add(departmentId); }
        sql.append(" order by s.work_date, s.period");
        return jdbc.query(sql.toString(), (rs, row) -> mapSchedule(rs), args.toArray());
    }

    public Schedule findSchedule(String id) {
        return jdbc.query("""
                select s.id, s.staff_id, d.name as doctor_name, s.department_id,
                       s.work_date, s.period, s.capacity, s.status
                from schedule s join staff d on d.id = s.staff_id where s.id = ?
                """, (rs, row) -> mapSchedule(rs), id).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("排班不存在"));
    }

    private Schedule mapSchedule(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Schedule(rs.getString("id"), rs.getString("staff_id"), rs.getString("doctor_name"),
                rs.getString("department_id"), rs.getDate("work_date").toLocalDate(),
                rs.getString("period"), rs.getInt("capacity"), rs.getString("status"));
    }

    // ── 时间槽 ─────────────────────────────────────────────────────────
    public List<ScheduleTimeSlot> timeSlots(String scheduleId) {
        return jdbc.query("""
                select id, schedule_id, start_time, capacity
                from schedule_slot where schedule_id = ? order by start_time
                """, (rs, row) -> new ScheduleTimeSlot(
                rs.getString("id"), rs.getString("schedule_id"),
                rs.getObject("start_time", LocalTime.class), rs.getInt("capacity")), scheduleId);
    }

    public List<ScheduleTimeSlot> timeSlots(List<String> scheduleIds) {
        if (scheduleIds == null || scheduleIds.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(scheduleIds.size(), "?"));
        return jdbc.query("""
                select id, schedule_id, start_time, capacity
                from schedule_slot where schedule_id in (""" + placeholders + """
                ) order by schedule_id, start_time
                """, (rs, row) -> new ScheduleTimeSlot(
                rs.getString("id"), rs.getString("schedule_id"),
                rs.getObject("start_time", LocalTime.class), rs.getInt("capacity")),
                scheduleIds.toArray());
    }

    public Schedule createSchedule(String doctorId, String departmentId,
            LocalDate date, String period, int capacity) {
        String id = "schedule-" + UUID.randomUUID();
        jdbc.update("insert into schedule (id,staff_id,department_id,work_date,period,capacity) values (?,?,?,?,?,?)",
                id, doctorId, departmentId, date, period, capacity);
        createDefaultTimeSlots(id, period, capacity);
        return findSchedule(id);
    }

    private void createDefaultTimeSlots(String scheduleId, String period, int capacity) {
        List<LocalTime> starts = defaultStartTimes(period);
        int base = Math.max(1, capacity / starts.size());
        int remainder = capacity % starts.size();
        for (int i = 0; i < starts.size(); i++) {
            LocalTime start = starts.get(i);
            int slotCapacity = base + (i < remainder ? 1 : 0);
            jdbc.update("""
                    insert into schedule_slot (id, schedule_id, start_time, capacity)
                    values (?, ?, ?, ?)
                    on conflict (schedule_id, start_time) do update set capacity = excluded.capacity
                    """, scheduleId + "-" + start.toString().replace(":", ""), scheduleId, start, slotCapacity);
        }
    }

    private List<LocalTime> defaultStartTimes(String period) {
        if ("上午".equals(period)) return List.of(LocalTime.of(8,0), LocalTime.of(8,30), LocalTime.of(9,0), LocalTime.of(9,30));
        if ("下午".equals(period)) return List.of(LocalTime.of(14,0), LocalTime.of(14,30), LocalTime.of(15,0), LocalTime.of(15,30));
        return List.of(LocalTime.of(8,0), LocalTime.of(8,30), LocalTime.of(9,0), LocalTime.of(9,30),
                       LocalTime.of(14,0), LocalTime.of(14,30), LocalTime.of(15,0), LocalTime.of(15,30));
    }

    public Schedule suspendSchedule(String id, String reason) {
        if (jdbc.update("update schedule set status='SUSPENDED', suspension_reason=? where id=? and status='PUBLISHED'",
                reason, id) != 1) throw new IllegalArgumentException("排班不存在或已停诊");
        return findSchedule(id);
    }

    public Schedule reschedule(String id, LocalDate date, String period) {
        if (jdbc.update("update schedule set work_date=?, period=? where id=? and status='PUBLISHED'",
                date, period, id) != 1) throw new IllegalArgumentException("仅已发布排班允许调班");
        Schedule schedule = findSchedule(id);
        jdbc.update("delete from schedule_slot where schedule_id = ?", id);
        createDefaultTimeSlots(id, period, schedule.capacity());
        return schedule;
    }

    public record Department(String id, String name, String description) {}
    public record Doctor(String id, String employeeNo, String name, String title,
            String departmentId, String departmentName, String specialty, String roleType) {}
    public record Schedule(String id, String doctorId, String doctorName, String departmentId,
            LocalDate workDate, String period, int capacity, String status) {}
    public record ScheduleTimeSlot(String id, String scheduleId, LocalTime startTime, int capacity) {}
}
