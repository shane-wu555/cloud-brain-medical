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

    public Appointment save(Appointment appointment) {
        jdbcTemplate.update("""
                insert into appointment (
                    id, schedule_id, patient_id, patient_name, doctor_id, doctor_name, department_id, department_name,
                    visit_date, period, source, status, payment_status, payment_method, triage_summary, risk_level,
                    recommended_department_id, queue_number, missed_count, paid_at, cancelled_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                appointment.getCancelledAt());
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
                    AppointmentSource.valueOf(rs.getString("source")),
                    AppointmentStatus.valueOf(rs.getString("status")),
                    PaymentStatus.valueOf(rs.getString("payment_status")),
                    rs.getString("triage_summary"),
                    rs.getString("risk_level"),
                    rs.getString("recommended_department_id"),
                    rs.getInt("queue_number"));
            appointment.restorePersistenceState(rs.getString("payment_method"), rs.getInt("missed_count"));
            if (appointment.getStatus() == AppointmentStatus.FINISHED) {
                appointment.markFinished();
            }
            return appointment;
        }
    }
}
