package com.cloudbrain.doctor.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DoctorCatalogRepository {
    private final JdbcTemplate jdbc;
    public DoctorCatalogRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Department> departments() {
        return jdbc.query("select id,name,description from department where active order by name",
                (rs, row) -> new Department(rs.getString(1), rs.getString(2), rs.getString(3)));
    }
    public Department createDepartment(String name, String description) {
        String id = "dept-" + UUID.randomUUID();
        jdbc.update("insert into department (id,name,description) values (?,?,?)", id, name, description);
        return new Department(id, name, description);
    }
    public List<Doctor> doctors(String departmentId) {
        StringBuilder sql = new StringBuilder("""
                select d.id,d.employee_no,d.name,d.title,d.department_id,p.name,d.specialty,d.role_type
                from outpatient_doctor d join department p on p.id=d.department_id
                where d.active
                """);
        List<Object> args = new ArrayList<>();
        if (departmentId != null && !departmentId.isBlank()) {
            sql.append(" and d.department_id = ?");
            args.add(departmentId);
        }
        sql.append(" order by d.name");
        return jdbc.query(sql.toString(), (rs, row) -> new Doctor(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8)), args.toArray());
    }
    public Doctor createDoctor(String employeeNo, String name, String title, String departmentId, String roleType, String specialty) {
        String id = UUID.randomUUID().toString();
        jdbc.update("insert into outpatient_doctor (id,employee_no,name,title,department_id,role_type,specialty) values (?,?,?,?,?,?,?)",
                id, employeeNo, name, title, departmentId, roleType, specialty);
        return doctors(departmentId).stream().filter(d -> d.id().equals(id)).findFirst().orElseThrow();
    }
    public List<Schedule> schedules(String doctorId, String departmentId) {
        StringBuilder sql = new StringBuilder("""
                select s.id,s.doctor_id,d.name,s.department_id,s.work_date,s.period,s.capacity,s.status
                from doctor_schedule s join outpatient_doctor d on d.id=s.doctor_id
                where 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (doctorId != null && !doctorId.isBlank()) {
            sql.append(" and s.doctor_id = ?");
            args.add(doctorId);
        }
        if (departmentId != null && !departmentId.isBlank()) {
            sql.append(" and s.department_id = ?");
            args.add(departmentId);
        }
        sql.append(" order by s.work_date,s.period");
        return jdbc.query(sql.toString(), (rs, row) -> mapSchedule(rs), args.toArray());
    }
    public Schedule findSchedule(String id) {
        return jdbc.query("""
                select s.id,s.doctor_id,d.name,s.department_id,s.work_date,s.period,s.capacity,s.status
                from doctor_schedule s join outpatient_doctor d on d.id=s.doctor_id where s.id=?
                """, (rs, row) -> mapSchedule(rs), id).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("排班不存在"));
    }
    private Schedule mapSchedule(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Schedule(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getDate(5).toLocalDate(), rs.getString(6), rs.getInt(7), rs.getString(8));
    }
    public List<ScheduleTimeSlot> timeSlots(String scheduleId) {
        return jdbc.query("""
                select id, schedule_id, start_time, capacity
                from doctor_schedule_time_slot
                where schedule_id = ?
                order by start_time
                """, (rs, row) -> new ScheduleTimeSlot(
                rs.getString("id"),
                rs.getString("schedule_id"),
                rs.getObject("start_time", LocalTime.class),
                rs.getInt("capacity")), scheduleId);
    }
    public List<ScheduleTimeSlot> timeSlots(List<String> scheduleIds) {
        if (scheduleIds == null || scheduleIds.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(scheduleIds.size(), "?"));
        return jdbc.query("""
                select id, schedule_id, start_time, capacity
                from doctor_schedule_time_slot
                where schedule_id in (
                """ + placeholders + """
                )
                order by schedule_id, start_time
                """, (rs, row) -> new ScheduleTimeSlot(
                rs.getString("id"),
                rs.getString("schedule_id"),
                rs.getObject("start_time", LocalTime.class),
                rs.getInt("capacity")), scheduleIds.toArray());
    }
    public Schedule createSchedule(String doctorId, String departmentId, LocalDate date, String period, int capacity) {
        String id = "schedule-" + UUID.randomUUID();
        jdbc.update("insert into doctor_schedule (id,doctor_id,department_id,work_date,period,capacity) values (?,?,?,?,?,?)",
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
                    insert into doctor_schedule_time_slot (id, schedule_id, start_time, capacity)
                    values (?, ?, ?, ?)
                    on conflict (schedule_id, start_time) do update set capacity = excluded.capacity
                    """, scheduleId + "-" + start.toString().replace(":", ""), scheduleId, start, slotCapacity);
        }
    }
    private List<LocalTime> defaultStartTimes(String period) {
        String value = period == null ? "" : period.trim().toUpperCase();
        if ("上午".equals(period) || "MORNING".equals(value)) {
            return List.of(LocalTime.of(8, 0), LocalTime.of(8, 30), LocalTime.of(9, 0), LocalTime.of(9, 30));
        }
        if ("下午".equals(period) || "AFTERNOON".equals(value)) {
            return List.of(LocalTime.of(14, 0), LocalTime.of(14, 30), LocalTime.of(15, 0), LocalTime.of(15, 30));
        }
        return List.of(LocalTime.of(8, 0), LocalTime.of(8, 30), LocalTime.of(9, 0), LocalTime.of(9, 30),
                LocalTime.of(14, 0), LocalTime.of(14, 30), LocalTime.of(15, 0), LocalTime.of(15, 30));
    }
    public Schedule suspendSchedule(String id, String reason) {
        if (jdbc.update("update doctor_schedule set status='SUSPENDED',suspension_reason=?,updated_at=now() where id=? and status='PUBLISHED'",
                reason, id) != 1) throw new IllegalArgumentException("排班不存在或已停诊");
        return findSchedule(id);
    }
    public Schedule reschedule(String id, LocalDate date, String period) {
        if (jdbc.update("update doctor_schedule set work_date=?,period=?,updated_at=now() where id=? and status='PUBLISHED'",
                date, period, id) != 1) throw new IllegalArgumentException("仅已发布排班允许调班");
        Schedule schedule = findSchedule(id);
        jdbc.update("delete from doctor_schedule_time_slot where schedule_id = ?", id);
        createDefaultTimeSlots(id, period, schedule.capacity());
        return schedule;
    }

    public record Department(String id,String name,String description) {}
    public record Doctor(String id,String employeeNo,String name,String title,String departmentId,String departmentName,String specialty,String roleType) {}
    public record Schedule(String id,String doctorId,String doctorName,String departmentId,LocalDate workDate,String period,int capacity,String status) {}
    public record ScheduleTimeSlot(String id,String scheduleId,LocalTime startTime,int capacity) {}
}
