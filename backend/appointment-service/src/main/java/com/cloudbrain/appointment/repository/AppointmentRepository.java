package com.cloudbrain.appointment.repository;

import com.cloudbrain.appointment.entity.Appointment;
import com.cloudbrain.appointment.entity.AppointmentSource;
import com.cloudbrain.appointment.entity.AppointmentStatus;
import com.cloudbrain.appointment.entity.PaymentStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
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
        List<Appointment> result = jdbcTemplate.query("select * from appointment where id = ?", rowMapper, id);
        return result.stream().findFirst();
    }

    public Optional<Appointment> findByIdForUpdate(String id) {
        List<Appointment> result = jdbcTemplate.query("select * from appointment where id = ? for update", rowMapper, id);
        return result.stream().findFirst();
    }

    public List<String> findExpiredPendingIds() {
        return jdbcTemplate.query("""
                select id from appointment
                where status='PENDING_PAYMENT' and lock_expires_at <= now()
                order by lock_expires_at limit 100
                """,(rs,row)->rs.getString(1));
    }

    public boolean existsActiveInPeriod(String patientId, String visitDate, String period) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from appointment
                where patient_id = ?
                  and visit_date = ?::date
                  and period = ?
                  and status <> 'CANCELLED'
                """, Integer.class, patientId, visitDate, period);
        return count != null && count > 0;
    }

    public Appointment save(Appointment appointment) {
        jdbcTemplate.update("""
                insert into appointment (
                    id, schedule_id, patient_id, patient_name, doctor_id, doctor_name, department_id, department_name,
                    visit_date, period, start_time, source, status, payment_status, payment_method, triage_summary, risk_level,
                    recommended_department_id, queue_number, missed_count, paid_at, cancelled_at, lock_expires_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
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
                appointment.getScheduleId(),
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
                "select business_no from appointment where id=?",String.class,appointment.getId()));
        return appointment;
    }

    public int size() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from appointment", Integer.class);
        return count == null ? 0 : count;
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
            jdbcTemplate.update("update appointment set missed_count=missed_count+1,status='WAITING' where id=?",id);
            return findById(id).orElseThrow();
        }
        int from=current.getQueueNumber(),target=next.get(next.size()-1);
        jdbcTemplate.update("update appointment set queue_number=? where id=?",-1000000-from,id);
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
        jdbcTemplate.update("update appointment set queue_number=?,missed_count=missed_count+1,status='WAITING' where id=?",target,id);
        return findById(id).orElseThrow();
    }

    private static class AppointmentRowMapper implements RowMapper<Appointment> {
        @Override
        public Appointment mapRow(ResultSet rs, int rowNum) throws SQLException {
            Appointment appointment = new Appointment(
                    rs.getString("id"),
                    rs.getString("schedule_id"),
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
                    AppointmentStatus.valueOf(rs.getString("status")),
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
