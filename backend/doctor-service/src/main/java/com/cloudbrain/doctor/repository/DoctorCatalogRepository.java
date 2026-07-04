package com.cloudbrain.doctor.repository;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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
    @Cacheable(cacheNames = "doctor:departments")
    public List<Department> departments() {
        return jdbc.query("select id,name,description from department where active order by name",
                (rs, row) -> new Department(rs.getString(1), rs.getString(2), rs.getString(3)));
    }
    @Cacheable(cacheNames = "doctor:schedulingDepartments")
    public List<Department> schedulingDepartments() {
        return jdbc.query("""
                select distinct d.id, d.name, d.description
                from department d
                join staff s on s.department_id = d.id
                where d.active and s.active and s.role_type = 'OUTPATIENT_DOCTOR'
                  and d.name not in ('影像检查科','检验科','处置科','药房','系统管理','收费处')
                order by d.name
                """, (rs, row) -> new Department(rs.getString(1), rs.getString(2), rs.getString(3)));
    }
    @CacheEvict(cacheNames = {
            "doctor:departments",
            "doctor:schedulingDepartments",
            "doctor:doctors",
            "doctor:schedules"
    }, allEntries = true)
    public Department createDepartment(String name, String description) {
        String id = "dept-" + UUID.randomUUID();
        jdbc.update("insert into department (id,name,description) values (?,?,?)", id, name, description);
        return new Department(id, name, description);
    }

    // ── 医生（staff 表，仅门诊医生）────────────────────────────────────
    @Cacheable(cacheNames = "doctor:doctors", key = "#p0 == null ? 'all' : #p0")
    public List<Doctor> doctors(String departmentId) {
        StringBuilder sql = new StringBuilder("""
                select s.id, s.employee_no, s.name, s.title, s.department_id, p.name as dept_name,
                       s.specialty, s.role_type, r.id as room_id, r.name as room_name
                from staff s
                join department p on p.id = s.department_id
                left join outpatient_doctor od on od.staff_id = s.id
                left join outpatient_room r on r.id = od.room_id and r.active
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
                rs.getString("specialty"), rs.getString("role_type"),
                rs.getString("room_id"), rs.getString("room_name")), args.toArray());
    }

    @CacheEvict(cacheNames = {
            "doctor:schedulingDepartments",
            "doctor:doctors",
            "doctor:doctorDetails",
            "doctor:outpatientRooms",
            "doctor:schedules",
            "doctor:timeSlots"
    }, allEntries = true)
    public Doctor createDoctor(String employeeNo, String name, String title,
            String departmentId, String roleType, String specialty) {
        validateSchedulingDepartment(departmentId);
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

    @Cacheable(cacheNames = "doctor:doctorDetails", key = "#p0")
    public Doctor findDoctor(String id) {
        return jdbc.query("""
                select s.id, s.employee_no, s.name, s.title, s.department_id, p.name as dept_name,
                       s.specialty, s.role_type, r.id as room_id, r.name as room_name
                from staff s
                join department p on p.id = s.department_id
                left join outpatient_doctor od on od.staff_id = s.id
                left join outpatient_room r on r.id = od.room_id and r.active
                where s.active and s.role_type = 'OUTPATIENT_DOCTOR' and s.id = ?
                """, (rs, row) -> new Doctor(
                rs.getString("id"), rs.getString("employee_no"), rs.getString("name"),
                rs.getString("title"), rs.getString("department_id"), rs.getString("dept_name"),
                rs.getString("specialty"), rs.getString("role_type"),
                rs.getString("room_id"), rs.getString("room_name")), id).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("医生不存在"));
    }

    @CacheEvict(cacheNames = {
            "doctor:schedulingDepartments",
            "doctor:doctors",
            "doctor:doctorDetails",
            "doctor:outpatientRooms",
            "doctor:schedules",
            "doctor:timeSlots"
    }, allEntries = true)
    public Doctor updateDoctor(String id, String name, String title, String departmentId, String specialty) {
        validateSchedulingDepartment(departmentId);
        if (jdbc.update("""
                update staff
                set name = ?, title = ?, department_id = ?, specialty = ?
                where id = ? and active and role_type = 'OUTPATIENT_DOCTOR'
                """, name, title, departmentId, specialty, id) != 1) {
            throw new IllegalArgumentException("医生不存在");
        }
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
        return findDoctor(id);
    }

    // ── 请假/手术安排 ─────────────────────────────────────────────────
    @Cacheable(cacheNames = "doctor:doctorEvents", key = "'all'")
    public List<DoctorEvent> doctorEvents() {
        String sql = """
                select e.id, e.staff_id, s.name as doctor_name, d.name as department_name,
                       e.event_type, e.note, sl.event_date, sl.period
                from doctor_event e
                join staff s on s.id = e.staff_id
                join department d on d.id = s.department_id
                join doctor_event_slot sl on sl.event_id = e.id
                where sl.event_date > current_date
                order by sl.event_date, sl.period, s.name
                """;
        java.util.LinkedHashMap<String, DoctorEventBuilder> map = new java.util.LinkedHashMap<>();
        jdbc.query(sql, rs -> {
            String id = rs.getString("id");
            DoctorEventBuilder builder = map.get(id);
            if (builder == null) {
                builder = new DoctorEventBuilder(
                        id,
                        rs.getString("staff_id"),
                        rs.getString("doctor_name"),
                        rs.getString("department_name"),
                        rs.getString("event_type"),
                        rs.getString("note"));
                map.put(id, builder);
            }
            builder.addSlot(rs.getDate("event_date").toLocalDate(), rs.getString("period"));
        });
        return map.values().stream().map(DoctorEventBuilder::build).toList();
    }

    @Cacheable(cacheNames = "doctor:doctorEvents", key = "'window:' + #p0 + ':' + #p1")
    public List<DoctorEvent> doctorEvents(LocalDate startInclusive, LocalDate endInclusive) {
        return doctorEvents().stream()
                .map(event -> event.withDates(event.dates().stream()
                        .filter(date -> !date.isBefore(startInclusive) && !date.isAfter(endInclusive))
                        .toList()))
                .filter(event -> !event.dates().isEmpty())
                .toList();
    }

    @CacheEvict(cacheNames = {
            "doctor:doctorEvents",
            "doctor:schedules"
    }, allEntries = true)
    public DoctorEvent createDoctorEvent(String doctorId, String eventType,
            List<LocalDate> dates, List<String> periods, String note) {
        validateDoctorEvent(doctorId, eventType, dates, periods);
        String id = "doctor-event-" + UUID.randomUUID();
        jdbc.update("insert into doctor_event (id, staff_id, event_type, note) values (?,?,?,?)",
                id, doctorId, eventType, note);
        replaceDoctorEventSlots(id, dates, periods);
        return findDoctorEvent(id);
    }

    @CacheEvict(cacheNames = {
            "doctor:doctorEvents",
            "doctor:schedules"
    }, allEntries = true)
    public DoctorEvent updateDoctorEvent(String id, String doctorId, String eventType,
            List<LocalDate> dates, List<String> periods, String note) {
        validateDoctorEvent(doctorId, eventType, dates, periods);
        if (jdbc.update("""
                update doctor_event
                set staff_id = ?, event_type = ?, note = ?, updated_at = now()
                where id = ?
                """, doctorId, eventType, note, id) != 1) {
            throw new IllegalArgumentException("安排不存在");
        }
        replaceDoctorEventSlots(id, dates, periods);
        return findDoctorEvent(id);
    }

    @CacheEvict(cacheNames = {
            "doctor:doctorEvents",
            "doctor:schedules"
    }, allEntries = true)
    public void deleteDoctorEvent(String id) {
        if (jdbc.update("delete from doctor_event where id = ?", id) != 1) {
            throw new IllegalArgumentException("安排不存在");
        }
    }

    private DoctorEvent findDoctorEvent(String id) {
        return doctorEvents().stream().filter(event -> event.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("安排不存在或已不在未来日期内"));
    }

    private void replaceDoctorEventSlots(String eventId, List<LocalDate> dates, List<String> periods) {
        jdbc.update("delete from doctor_event_slot where event_id = ?", eventId);
        for (LocalDate date : uniqueDates(dates)) {
            for (String period : uniquePeriods(periods)) {
                jdbc.update("""
                        insert into doctor_event_slot (id, event_id, event_date, period)
                        values (?, ?, ?, ?)
                        """, eventId + "-" + date + "-" + period, eventId, date, period);
            }
        }
    }

    private void validateDoctorEvent(String doctorId, String eventType, List<LocalDate> dates, List<String> periods) {
        findDoctor(doctorId);
        if (!"LEAVE".equals(eventType) && !"SURGERY".equals(eventType)) {
            throw new IllegalArgumentException("安排类型不正确");
        }
        if (uniqueDates(dates).isEmpty()) {
            throw new IllegalArgumentException("请选择日期");
        }
        LocalDate earliest = LocalDate.now().plusDays(7);
        if (uniqueDates(dates).stream().anyMatch(date -> date.isBefore(earliest))) {
            throw new IllegalArgumentException("请假/手术日期必须在当前日期 7 天之后");
        }
        if (uniquePeriods(periods).isEmpty()) {
            throw new IllegalArgumentException("请选择上午或下午");
        }
    }

    private List<LocalDate> uniqueDates(List<LocalDate> dates) {
        if (dates == null) return List.of();
        return dates.stream().filter(java.util.Objects::nonNull).distinct().sorted().toList();
    }

    private List<String> uniquePeriods(List<String> periods) {
        if (periods == null) return List.of();
        return periods.stream()
                .filter(period -> "上午".equals(period) || "下午".equals(period))
                .distinct()
                .toList();
    }

    @Cacheable(cacheNames = "doctor:outpatientRooms", key = "'department:' + #p0")
    public List<OutpatientRoom> outpatientRoomsWithDoctors(String departmentId) {
        return jdbc.query("""
                select distinct r.id, r.department_id, r.name, r.location
                from outpatient_room r
                join outpatient_doctor od on od.room_id = r.id
                join staff s on s.id = od.staff_id
                where r.active and s.active and s.role_type = 'OUTPATIENT_DOCTOR' and r.department_id = ?
                order by r.name
                """, (rs, row) -> new OutpatientRoom(
                        rs.getString("id"), rs.getString("department_id"),
                        rs.getString("name"), rs.getString("location")),
                departmentId);
    }

    @Cacheable(cacheNames = "doctor:outpatientRooms", key = "'doctor:' + #p0")
    public OutpatientRoom outpatientRoomForDoctor(String doctorId) {
        return jdbc.query("""
                select r.id, r.department_id, r.name, r.location
                from outpatient_doctor od
                join outpatient_room r on r.id = od.room_id and r.active
                where od.staff_id = ?
                """, (rs, row) -> new OutpatientRoom(
                        rs.getString("id"), rs.getString("department_id"),
                        rs.getString("name"), rs.getString("location")),
                doctorId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("医生未绑定门诊诊室"));
    }

    // ── 排班 ───────────────────────────────────────────────────────────
    public DoctorOperationsStats doctorOperationsStats(LocalDate workDate) {
        Integer activeDoctors = jdbc.queryForObject("""
                select count(distinct s.staff_id)
                from schedule s
                join staff st on st.id = s.staff_id and st.active and st.role_type = 'OUTPATIENT_DOCTOR'
                where s.work_date = ? and s.status <> 'SUSPENDED'
                """, Integer.class, workDate);
        Integer scheduledRooms = jdbc.queryForObject("""
                select count(distinct r.id)
                from schedule s
                join outpatient_doctor od on od.staff_id = s.staff_id
                join outpatient_room r on r.id = od.room_id and r.active
                join staff st on st.id = s.staff_id and st.active and st.role_type = 'OUTPATIENT_DOCTOR'
                where s.work_date = ? and s.status <> 'SUSPENDED'
                """, Integer.class, workDate);
        Integer totalRooms = jdbc.queryForObject("""
                select count(distinct r.id)
                from outpatient_room r
                join outpatient_doctor od on od.room_id = r.id
                join staff st on st.id = od.staff_id and st.active and st.role_type = 'OUTPATIENT_DOCTOR'
                where r.active
                """, Integer.class);
        int doctors = activeDoctors == null ? 0 : activeDoctors;
        int coveredRooms = scheduledRooms == null ? 0 : scheduledRooms;
        int rooms = totalRooms == null ? 0 : totalRooms;
        int coverageRate = rooms == 0 ? 0 : Math.max(0, Math.min(100, Math.round((float) coveredRooms * 100 / rooms)));
        return new DoctorOperationsStats(doctors, coveredRooms, rooms, coverageRate);
    }

    @Cacheable(cacheNames = "doctor:schedules", key = "'all:' + (#p0 == null ? '' : #p0) + ':' + (#p1 == null ? '' : #p1)")
    public List<Schedule> schedules(String doctorId, String departmentId) {
        StringBuilder sql = new StringBuilder("""
                select s.id, s.staff_id, d.name as doctor_name, s.department_id,
                       dept.name as department_name,
                       s.work_date, s.period, s.capacity, s.status,
                       r.id as room_id, r.name as room_name
                from schedule s
                join staff d on d.id = s.staff_id
                join department dept on dept.id = s.department_id
                left join outpatient_doctor od on od.staff_id = s.staff_id
                left join outpatient_room r on r.id = od.room_id and r.active
                where 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (doctorId != null && !doctorId.isBlank()) { sql.append(" and s.staff_id = ?"); args.add(doctorId); }
        if (departmentId != null && !departmentId.isBlank()) { sql.append(" and s.department_id = ?"); args.add(departmentId); }
        sql.append(" order by s.work_date, s.period");
        return jdbc.query(sql.toString(), (rs, row) -> mapSchedule(rs), args.toArray());
    }

    @Cacheable(cacheNames = "doctor:schedules", key = "'window:' + (#p0 == null ? '' : #p0) + ':' + (#p1 == null ? '' : #p1) + ':' + #p2 + ':' + #p3")
    public List<Schedule> schedules(String doctorId, String departmentId, LocalDate startInclusive, LocalDate endInclusive) {
        return schedules(doctorId, departmentId).stream()
                .filter(schedule -> !schedule.workDate().isBefore(startInclusive) && !schedule.workDate().isAfter(endInclusive))
                .toList();
    }

    @Cacheable(cacheNames = "doctor:schedules", key = "'detail:' + #p0")
    public Schedule findSchedule(String id) {
        return jdbc.query("""
                select s.id, s.staff_id, d.name as doctor_name, s.department_id,
                       dept.name as department_name,
                       s.work_date, s.period, s.capacity, s.status,
                       r.id as room_id, r.name as room_name
                from schedule s
                join staff d on d.id = s.staff_id
                join department dept on dept.id = s.department_id
                left join outpatient_doctor od on od.staff_id = s.staff_id
                left join outpatient_room r on r.id = od.room_id and r.active
                where s.id = ?
                """, (rs, row) -> mapSchedule(rs), id).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("排班不存在"));
    }

    private Schedule mapSchedule(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Schedule(rs.getString("id"), rs.getString("staff_id"), rs.getString("doctor_name"),
                rs.getString("department_id"), rs.getString("department_name"), rs.getDate("work_date").toLocalDate(),
                rs.getString("period"), rs.getInt("capacity"), rs.getString("status"),
                rs.getString("room_id"), rs.getString("room_name"));
    }

    // ── 时间槽 ─────────────────────────────────────────────────────────
    @Cacheable(cacheNames = "doctor:timeSlots", key = "#p0")
    public List<ScheduleTimeSlot> timeSlots(String scheduleId) {
        return jdbc.query("""
                select id, schedule_id, start_time, capacity
                from schedule_slot where schedule_id = ? order by start_time
                """, (rs, row) -> new ScheduleTimeSlot(
                rs.getString("id"), rs.getString("schedule_id"),
                rs.getObject("start_time", LocalTime.class), rs.getInt("capacity")), scheduleId);
    }

    @Cacheable(cacheNames = "doctor:timeSlots", key = "#p0 == null ? 'empty' : #p0.toString()")
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

    @CacheEvict(cacheNames = {
            "doctor:schedules",
            "doctor:timeSlots"
    }, allEntries = true)
    public Schedule createSchedule(String doctorId, String departmentId,
            LocalDate date, String period, int capacity) {
        validateSchedulingDepartment(departmentId);
        validateSchedulePeriod(period);
        validateDoctorCanScheduleOnDate(doctorId, date, period, null);
        validateRoomCanScheduleOnDate(doctorId, date, period, null);
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
        if ("上午".equals(period)) return List.of(LocalTime.of(8,0), LocalTime.of(8,30), LocalTime.of(9,0), LocalTime.of(9,30),
                LocalTime.of(10,0), LocalTime.of(10,30), LocalTime.of(11,0), LocalTime.of(11,30));
        if ("下午".equals(period)) return List.of(LocalTime.of(14,0), LocalTime.of(14,30), LocalTime.of(15,0), LocalTime.of(15,30),
                LocalTime.of(16,0), LocalTime.of(16,30));
        return List.of();
    }

    @CacheEvict(cacheNames = {
            "doctor:schedules",
            "doctor:timeSlots"
    }, allEntries = true)
    public void deleteSchedulesForDepartmentWindow(String departmentId, LocalDate startInclusive, LocalDate endInclusive) {
        validateSchedulingDepartment(departmentId);
        List<String> ids = jdbc.query("""
                select id from schedule
                where department_id = ? and work_date between ? and ?
                """, (rs, row) -> rs.getString("id"), departmentId, startInclusive, endInclusive);
        if (ids.isEmpty()) return;
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        jdbc.update("delete from schedule_slot where schedule_id in (" + placeholders + ")", ids.toArray());
        jdbc.update("delete from schedule where id in (" + placeholders + ")", ids.toArray());
    }

    @CacheEvict(cacheNames = {
            "doctor:schedules",
            "doctor:timeSlots"
    }, allEntries = true)
    public Schedule suspendSchedule(String id, String reason) {
        if (jdbc.update("update schedule set status='SUSPENDED', suspension_reason=? where id=? and status='PUBLISHED'",
                reason, id) != 1) throw new IllegalArgumentException("排班不存在或已停诊");
        return findSchedule(id);
    }

    @CacheEvict(cacheNames = {
            "doctor:schedules",
            "doctor:timeSlots"
    }, allEntries = true)
    public Schedule reschedule(String id, LocalDate date, String period) {
        validateSchedulePeriod(period);
        Schedule current = findSchedule(id);
        validateDoctorCanScheduleOnDate(current.doctorId(), date, period, id);
        validateRoomCanScheduleOnDate(current.doctorId(), date, period, id);
        if (jdbc.update("update schedule set work_date=?, period=? where id=? and status='PUBLISHED'",
                date, period, id) != 1) throw new IllegalArgumentException("仅已发布排班允许调班");
        Schedule schedule = findSchedule(id);
        jdbc.update("delete from schedule_slot where schedule_id = ?", id);
        createDefaultTimeSlots(id, period, schedule.capacity());
        return schedule;
    }

    private void validateSchedulePeriod(String period) {
        if (!List.of("上午", "下午").contains(period)) {
            throw new IllegalArgumentException("排班时段只能是上午或下午");
        }
    }

    private void validateDoctorCanScheduleOnDate(String doctorId, LocalDate date, String period, String ignoredScheduleId) {
        StringBuilder sql = new StringBuilder("""
                select count(*) from schedule
                where staff_id = ? and work_date = ? and status <> 'SUSPENDED'
                  and period = ?
                """);
        List<Object> args = new ArrayList<>(List.of(doctorId, date, period));
        if (ignoredScheduleId != null && !ignoredScheduleId.isBlank()) {
            sql.append(" and id <> ?");
            args.add(ignoredScheduleId);
        }
        Integer count = jdbc.queryForObject(sql.toString(), Integer.class, args.toArray());
        if (count != null && count > 0) {
            throw new IllegalArgumentException("同一医生同一日期同一时段只能安排一次");
        }
    }

    private void validateRoomCanScheduleOnDate(String doctorId, LocalDate date, String period, String ignoredScheduleId) {
        OutpatientRoom room = outpatientRoomForDoctor(doctorId);
        StringBuilder sql = new StringBuilder("""
                select count(*)
                from schedule s
                join outpatient_doctor od on od.staff_id = s.staff_id
                where od.room_id = ? and s.work_date = ? and s.status <> 'SUSPENDED'
                  and s.period = ?
                """);
        List<Object> args = new ArrayList<>(List.of(room.id(), date, period));
        if (ignoredScheduleId != null && !ignoredScheduleId.isBlank()) {
            sql.append(" and s.id <> ?");
            args.add(ignoredScheduleId);
        }
        Integer count = jdbc.queryForObject(sql.toString(), Integer.class, args.toArray());
        if (count != null && count > 0) {
            throw new IllegalArgumentException("同一诊室同一日期同一时段只能安排一名医生");
        }
    }

    private void validateSchedulingDepartment(String departmentId) {
        Boolean allowed = jdbc.queryForObject("""
                select count(*) > 0
                from department
                where id = ? and active
                  and name not in ('影像检查科','检验科','处置科','药房','系统管理','收费处')
                """, Boolean.class, departmentId);
        if (!Boolean.TRUE.equals(allowed)) {
            throw new IllegalArgumentException("该科室不参与门诊排班");
        }
    }

    public record Department(String id, String name, String description) implements Serializable {}
    public record Doctor(String id, String employeeNo, String name, String title,
            String departmentId, String departmentName, String specialty, String roleType,
            String roomId, String roomName) implements Serializable {}
    public record OutpatientRoom(String id, String departmentId, String name, String location) implements Serializable {}
    public record DoctorOperationsStats(int activeDoctors, int scheduledRooms, int totalRooms, int roomCoverageRate) implements Serializable {}
    public record DoctorEvent(String id, String doctorId, String doctorName, String departmentName,
            String eventType, List<LocalDate> dates, List<String> periods, String note) implements Serializable {
        public DoctorEvent withDates(List<LocalDate> dates) {
            return new DoctorEvent(id, doctorId, doctorName, departmentName, eventType, dates, periods, note);
        }
    }
    public record Schedule(String id, String doctorId, String doctorName, String departmentId, String departmentName,
            LocalDate workDate, String period, int capacity, String status, String roomId, String roomName) implements Serializable {}
    public record ScheduleTimeSlot(String id, String scheduleId, LocalTime startTime, int capacity) implements Serializable {}

    private static class DoctorEventBuilder {
        private final String id;
        private final String doctorId;
        private final String doctorName;
        private final String departmentName;
        private final String eventType;
        private final String note;
        private final List<LocalDate> dates = new ArrayList<>();
        private final List<String> periods = new ArrayList<>();

        private DoctorEventBuilder(String id, String doctorId, String doctorName,
                String departmentName, String eventType, String note) {
            this.id = id;
            this.doctorId = doctorId;
            this.doctorName = doctorName;
            this.departmentName = departmentName;
            this.eventType = eventType;
            this.note = note;
        }

        private void addSlot(LocalDate date, String period) {
            if (!dates.contains(date)) dates.add(date);
            if (!periods.contains(period)) periods.add(period);
        }

        private DoctorEvent build() {
            dates.sort(LocalDate::compareTo);
            return new DoctorEvent(id, doctorId, doctorName, departmentName, eventType,
                    List.copyOf(dates), List.copyOf(periods), note);
        }
    }
}
