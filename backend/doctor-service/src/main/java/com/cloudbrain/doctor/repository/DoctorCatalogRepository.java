package com.cloudbrain.doctor.repository;

import java.time.LocalDate;
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
                select d.id,d.name,d.title,d.department_id,p.name,d.specialty,d.role_type
                from doctor d join department p on p.id=d.department_id
                where d.active
                """);
        List<Object> args = new ArrayList<>();
        if (departmentId != null && !departmentId.isBlank()) {
            sql.append(" and d.department_id = ?");
            args.add(departmentId);
        }
        sql.append(" order by d.name");
        return jdbc.query(sql.toString(), (rs, row) -> new Doctor(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7)), args.toArray());
    }
    public Doctor createDoctor(String name, String title, String departmentId, String roleType, String specialty) {
        String id = "doctor-" + UUID.randomUUID();
        jdbc.update("insert into doctor (id,name,title,department_id,role_type,specialty) values (?,?,?,?,?,?)",
                id, name, title, departmentId, roleType, specialty);
        return doctors(departmentId).stream().filter(d -> d.id().equals(id)).findFirst().orElseThrow();
    }
    public List<Schedule> schedules(String doctorId, String departmentId) {
        StringBuilder sql = new StringBuilder("""
                select s.id,s.doctor_id,d.name,s.department_id,s.work_date,s.period,s.capacity,s.status
                from doctor_schedule s join doctor d on d.id=s.doctor_id
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
                from doctor_schedule s join doctor d on d.id=s.doctor_id where s.id=?
                """, (rs, row) -> mapSchedule(rs), id).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("排班不存在"));
    }
    private Schedule mapSchedule(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Schedule(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getDate(5).toLocalDate(), rs.getString(6), rs.getInt(7), rs.getString(8));
    }
    public Schedule createSchedule(String doctorId, String departmentId, LocalDate date, String period, int capacity) {
        String id = "schedule-" + UUID.randomUUID();
        jdbc.update("insert into doctor_schedule (id,doctor_id,department_id,work_date,period,capacity) values (?,?,?,?,?,?)",
                id, doctorId, departmentId, date, period, capacity);
        return findSchedule(id);
    }
    public Schedule suspendSchedule(String id, String reason) {
        if (jdbc.update("update doctor_schedule set status='SUSPENDED',suspension_reason=?,updated_at=now() where id=? and status='PUBLISHED'",
                reason, id) != 1) throw new IllegalArgumentException("排班不存在或已停诊");
        return findSchedule(id);
    }
    public Schedule reschedule(String id, LocalDate date, String period) {
        if (jdbc.update("update doctor_schedule set work_date=?,period=?,updated_at=now() where id=? and status='PUBLISHED'",
                date, period, id) != 1) throw new IllegalArgumentException("仅已发布排班允许调班");
        return findSchedule(id);
    }

    public record Department(String id,String name,String description) {}
    public record Doctor(String id,String name,String title,String departmentId,String departmentName,String specialty,String roleType) {}
    public record Schedule(String id,String doctorId,String doctorName,String departmentId,LocalDate workDate,String period,int capacity,String status) {}
}
