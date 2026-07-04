package com.cloudbrain.appointment.repository;

import com.cloudbrain.appointment.entity.Appointment;
import com.cloudbrain.appointment.entity.AppointmentSource;
import com.cloudbrain.appointment.entity.AppointmentStatus;
import com.cloudbrain.appointment.entity.PaymentStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AppointmentRepository {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<Appointment> rowMapper = new AppointmentRowMapper();

    public AppointmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Appointment> findAll() {
        return jdbcTemplate.query("select * from appointment order by visit_date, queue_number", rowMapper);
    }

    public Optional<Appointment> findById(String id) {
        List<Appointment> result = jdbcTemplate.query("select * from appointment where id = ?::uuid", rowMapper, id);
        return result.stream().findFirst();
    }

    public Optional<Appointment> findByIdForUpdate(String id) {
        List<Appointment> result = jdbcTemplate.query("select * from appointment where id = ?::uuid for update", rowMapper, id);
        return result.stream().findFirst();
    }

    public List<String> findExpiredPendingIds() {
        return jdbcTemplate.query("""
                select id from appointment
                where status='PENDING_PAYMENT' and lock_expires_at <= now()
                order by lock_expires_at limit 100
                """,(rs,row)->rs.getString(1));
    }

    public boolean existsActiveAtStartTime(String patientId, String visitDate, LocalTime startTime) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from appointment
                where patient_id = ?::uuid
                  and visit_date = ?::date
                  and start_time = ?
                  and status <> 'CANCELLED'
                """, Integer.class, patientId, visitDate, startTime);
        return count != null && count > 0;
    }

    public Appointment save(Appointment appointment) {
        jdbcTemplate.update("""
                insert into appointment (
                    id, slot_id, patient_id, patient_name, doctor_id, doctor_name, department_id, department_name,
                    visit_date, period, start_time, source, status, payment_status, payment_method, triage_summary, risk_level,
                    recommended_department_id, queue_number, missed_count, paid_at, cancelled_at, lock_expires_at
                )
                values (?::uuid, ?, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        case when ? = 'PENDING_PAYMENT' then now() + interval '15 minutes' else null end)
                on conflict (id) do update set
                    status = excluded.status,
                    payment_status = excluded.payment_status,
                    payment_method = excluded.payment_method,
                    queue_number = excluded.queue_number,
                    missed_count = excluded.missed_count,
                    paid_at = excluded.paid_at,
                    cancelled_at = excluded.cancelled_at
                """,
                appointment.getId(),
                appointment.getSlotId(),
                appointment.getPatientId(),
                appointment.getPatientName(),
                appointment.getDoctorId(),
                appointment.getDoctorName(),
                appointment.getDepartmentId(),
                appointment.getDepartmentName(),
                appointment.getVisitDate(),
                appointment.getPeriod(),
                appointment.getStartTime(),
                appointment.getSource().name(),
                appointment.getStatus().name(),
                appointment.getPaymentStatus().name(),
                appointment.getPaymentMethod(),
                appointment.getTriageSummary(),
                appointment.getRiskLevel(),
                appointment.getRecommendedDepartmentId(),
                appointment.getQueueNumber(),
                appointment.getMissedCount(),
                appointment.getPaidAt(),
                appointment.getCancelledAt(),
                appointment.getStatus().name());
            appointment.restoreBusinessNo(jdbcTemplate.queryForObject(
                "select business_no from appointment where id=?::uuid",String.class,appointment.getId()));
        return appointment;
    }

    public int size() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from appointment", Integer.class);
        return count == null ? 0 : count;
    }

    public DashboardAppointmentStats dashboardStats(LocalDate visitDate) {
        Integer todayAppointments = jdbcTemplate.queryForObject("""
                select count(*)
                from appointment
                where visit_date = ?
                  and status in ('WAITING','CALLED','IN_VISIT','REVISIT_WAITING','FINISHED')
                """, Integer.class, visitDate);
        Integer waitingVisits = jdbcTemplate.queryForObject("""
                select count(*)
                from appointment
                where visit_date = ?
                  and status in ('WAITING','CALLED','IN_VISIT','REVISIT_WAITING')
                """, Integer.class, visitDate);
        Integer aiTriageCount = jdbcTemplate.queryForObject("""
                select count(*)
                from appointment
                where visit_date = ?
                  and status in ('WAITING','CALLED','IN_VISIT','REVISIT_WAITING','FINISHED')
                  and nullif(trim(coalesce(triage_summary, '')), '') is not null
                  and trim(triage_summary) <> '窗口线下挂号'
                """, Integer.class, visitDate);
        List<DepartmentLoad> departmentLoads = jdbcTemplate.query("""
                select department_name, count(*) as visit_count
                from appointment
                where visit_date = ?
                  and status in ('WAITING','CALLED','IN_VISIT','REVISIT_WAITING','FINISHED')
                group by department_name
                order by visit_count desc, department_name
                """, (rs, row) -> new DepartmentLoad(rs.getString("department_name"), rs.getInt("visit_count")), visitDate);
        return new DashboardAppointmentStats(
                todayAppointments == null ? 0 : todayAppointments,
                waitingVisits == null ? 0 : waitingVisits,
                aiTriageCount == null ? 0 : aiTriageCount,
                departmentLoads);
    }

    public SchedulingHistorySummary schedulingHistorySummary(int lookbackDays) {
        int days = Math.max(7, Math.min(365, lookbackDays));
        Integer sampleSize = jdbcTemplate.queryForObject("""
                select count(*)
                from appointment
                where status <> 'CANCELLED'
                  and visit_date >= current_date - ?
                """, Integer.class, days);
        List<DoctorVisitAverage> doctorAverages = jdbcTemplate.query("""
                select doctor_id, max(doctor_name) as doctor_name, department_id, max(department_name) as department_name,
                       round(avg(daily_count))::int as average_visits
                from (
                    select doctor_id, doctor_name, department_id, department_name, visit_date, count(*) as daily_count
                    from appointment
                    where status <> 'CANCELLED'
                      and visit_date >= current_date - ?
                    group by doctor_id, doctor_name, department_id, department_name, visit_date
                ) daily
                group by doctor_id, department_id
                order by average_visits desc, doctor_name
                limit 100
                """, (rs, row) -> new DoctorVisitAverage(
                        rs.getString("doctor_id"),
                        rs.getString("doctor_name"),
                        rs.getString("department_id"),
                        rs.getString("department_name"),
                        rs.getInt("average_visits")),
                days);
        List<DepartmentVisitAverage> departmentAverages = jdbcTemplate.query("""
                select department_id, max(department_name) as department_name,
                       round(avg(daily_count))::int as average_visits
                from (
                    select department_id, department_name, visit_date, count(*) as daily_count
                    from appointment
                    where status <> 'CANCELLED'
                      and visit_date >= current_date - ?
                    group by department_id, department_name, visit_date
                ) daily
                group by department_id
                order by average_visits desc, department_name
                """, (rs, row) -> new DepartmentVisitAverage(
                        rs.getString("department_id"),
                        rs.getString("department_name"),
                        rs.getInt("average_visits")),
                days);
        List<WeekdayVisitAverage> weekdayAverages = jdbcTemplate.query("""
                select dow, round(avg(daily_count))::int as average_visits
                from (
                    select extract(isodow from visit_date)::int as dow, visit_date, count(*) as daily_count
                    from appointment
                    where status <> 'CANCELLED'
                      and visit_date >= current_date - ?
                    group by extract(isodow from visit_date)::int, visit_date
                ) daily
                group by dow
                order by dow
                """, (rs, row) -> new WeekdayVisitAverage(rs.getInt("dow"), rs.getInt("average_visits")), days);
        int samples = sampleSize == null ? 0 : sampleSize;
        return new SchedulingHistorySummary(days, samples, samples >= 200, doctorAverages, departmentAverages, weekdayAverages);
    }

    public int nextQueueNumber(String doctorId, String visitDate) {
        jdbcTemplate.query(
                "select pg_advisory_xact_lock(hashtext(?))",
                resultSet -> null,
                doctorId + ":" + visitDate);
        Integer max = jdbcTemplate.queryForObject(
                "select coalesce(max(queue_number), 0) from appointment where doctor_id = ? and visit_date = ?::date",
                Integer.class,
                doctorId,
                visitDate);
        return (max == null ? 0 : max) + 1;
    }

    public Appointment insertForRevisit(String id, int positions) {
        Appointment appt = findById(id).orElseThrow(() -> new IllegalArgumentException("挂号记录不存在"));
        jdbcTemplate.query("select pg_advisory_xact_lock(hashtext(?))", rs -> null,
                appt.getDoctorId() + ":" + appt.getVisitDate());
        Appointment locked = findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("挂号记录不存在"));

        // Find queue number of currently-serving patient; fall back to min waiting
        Integer servingPos = jdbcTemplate.queryForObject("""
                select coalesce(
                    (select queue_number from appointment where doctor_id=? and visit_date=? and status='IN_VISIT' limit 1),
                    (select min(queue_number) from appointment where doctor_id=? and visit_date=? and status in ('WAITING','CALLED'))
                )
                """, Integer.class,
                locked.getDoctorId(), locked.getVisitDate(),
                locked.getDoctorId(), locked.getVisitDate());

        if (servingPos == null) {
            throw new IllegalStateException("医生今日出诊已结束，无法加入复诊队列，请改天重新挂号");
        }

        int target = servingPos + positions;
        // Push active patients at or beyond target back by one slot
        jdbcTemplate.update("""
                update appointment set queue_number = queue_number + 1
                where doctor_id=? and visit_date=?
                  and status in ('WAITING','CALLED','REVISIT_WAITING')
                  and queue_number >= ?
                """, locked.getDoctorId(), locked.getVisitDate(), target);

        locked.waitForRevisit(target);
        return save(locked);
    }

    public Appointment skipByPositions(String id,int positions) {
        Appointment current=findById(id).orElseThrow(()->new IllegalArgumentException("挂号记录不存在"));
        jdbcTemplate.query("select pg_advisory_xact_lock(hashtext(?))",rs->null,
                current.getDoctorId()+":"+current.getVisitDate());
        current=findByIdForUpdate(id).orElseThrow(()->new IllegalArgumentException("挂号记录不存在"));
        if(current.getStatus()!=AppointmentStatus.WAITING && current.getStatus()!=AppointmentStatus.CALLED)
            throw new IllegalStateException("只有待接诊患者可以过号");
        List<Integer> next=jdbcTemplate.query("""
                select queue_number from appointment
                where doctor_id=? and visit_date=? and status in ('WAITING','CALLED') and queue_number>?
                order by queue_number limit ?
                """,(rs,row)->rs.getInt(1),current.getDoctorId(),current.getVisitDate(),current.getQueueNumber(),positions);
        if(next.isEmpty()) {
            jdbcTemplate.update("update appointment set missed_count=missed_count+1,status='WAITING' where id=?::uuid",id);
            return findById(id).orElseThrow();
        }
        int from=current.getQueueNumber(),target=next.get(next.size()-1);
        jdbcTemplate.update("update appointment set queue_number=? where id=?::uuid",-1000000-from,id);
        jdbcTemplate.update("""
                update appointment set queue_number=queue_number+1000000
                where doctor_id=? and visit_date=? and status in ('WAITING','CALLED')
                  and queue_number>? and queue_number<=?
                """,current.getDoctorId(),current.getVisitDate(),from,target);
        jdbcTemplate.update("""
                update appointment set queue_number=queue_number-1000001
                where doctor_id=? and visit_date=? and status in ('WAITING','CALLED')
                  and queue_number>? and queue_number<=?
                """,current.getDoctorId(),current.getVisitDate(),from+1000000,target+1000000);
        jdbcTemplate.update("update appointment set queue_number=?,missed_count=missed_count+1,status='WAITING' where id=?::uuid",target,id);
        return findById(id).orElseThrow();
    }

    private static AppointmentStatus parseStatus(String raw) {
        try {
            return AppointmentStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return AppointmentStatus.CANCELLED;
        }
    }

    public record SchedulingHistorySummary(
            int lookbackDays,
            int sampleSize,
            boolean trainingReady,
            List<DoctorVisitAverage> doctorAverages,
            List<DepartmentVisitAverage> departmentAverages,
            List<WeekdayVisitAverage> weekdayAverages) {}
    public record DashboardAppointmentStats(
            int todayAppointments,
            int waitingVisits,
            int aiTriageCount,
            List<DepartmentLoad> departmentLoads) {}
    public record DepartmentLoad(String name, int value) {}
    public record DoctorVisitAverage(String doctorId, String doctorName, String departmentId, String departmentName, int averageVisits) {}
    public record DepartmentVisitAverage(String departmentId, String departmentName, int averageVisits) {}
    public record WeekdayVisitAverage(int isoDow, int averageVisits) {}

    private static class AppointmentRowMapper implements RowMapper<Appointment> {
        @Override
        public Appointment mapRow(ResultSet rs, int rowNum) throws SQLException {
            Appointment appointment = new Appointment(
                    rs.getString("id"),
                    rs.getString("slot_id"),
                    rs.getString("patient_id"),
                    rs.getString("patient_name"),
                    rs.getString("doctor_id"),
                    rs.getString("doctor_name"),
                    rs.getString("department_id"),
                    rs.getString("department_name"),
                    rs.getDate("visit_date").toLocalDate(),
                    rs.getString("period"),
                    rs.getObject("start_time", java.time.LocalTime.class),
                    AppointmentSource.valueOf(rs.getString("source")),
                    parseStatus(rs.getString("status")),
                    PaymentStatus.valueOf(rs.getString("payment_status")),
                    rs.getString("triage_summary"),
                    rs.getString("risk_level"),
                    rs.getString("recommended_department_id"),
                    rs.getInt("queue_number"));
            appointment.restorePersistenceState(rs.getString("payment_method"), rs.getInt("missed_count"));
            appointment.restoreBusinessNo(rs.getString("business_no"));
            if (appointment.getStatus() == AppointmentStatus.FINISHED) {
                appointment.markFinished();
            }
            return appointment;
        }
    }
}
