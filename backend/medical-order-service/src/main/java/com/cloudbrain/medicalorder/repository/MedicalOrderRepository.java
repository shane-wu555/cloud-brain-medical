package com.cloudbrain.medicalorder.repository;

import com.cloudbrain.medicalorder.domain.MedicalOrder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MedicalOrderRepository {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<MedicalOrder> mapper = new Mapper();

    public MedicalOrderRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public MedicalOrder create(MedicalOrder order) {
        jdbcTemplate.update("""
                insert into medical_order
                    (id, appointment_id, patient_id, patient_name, ordering_doctor_id, order_type,
                     project_code, project_name, purpose, body_part, amount, payment_status, status)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, order.id(), order.appointmentId(), order.patientId(), order.patientName(), order.orderingDoctorId(),
                order.orderType(), order.projectCode(), order.projectName(), order.purpose(), order.bodyPart(), order.amount(),
                order.paymentStatus(), order.status());
        return findById(order.id()).orElseThrow();
    }

    public List<MedicalOrder> find(String type, String status, String patientId) {
        return jdbcTemplate.query("""
                select * from medical_order
                where (? is null or order_type = ?) and (? is null or status = ?) and (? is null or patient_id = ?)
                order by created_at
                """, mapper, type, type, status, status, patientId, patientId);
    }

    public Optional<MedicalOrder> findById(String id) {
        return jdbcTemplate.query("select * from medical_order where id = ?", mapper, id).stream().findFirst();
    }

    public boolean markPaid(String id) {
        return jdbcTemplate.update("update medical_order set payment_status = 'PAID', status = 'WAITING' where id = ? and status = 'PENDING_PAYMENT'", id) == 1;
    }

    public boolean start(String id, String executorId) {
        return jdbcTemplate.update("update medical_order set status = 'IN_PROGRESS', executor_id = ?, started_at = now() where id = ? and status = 'WAITING'", executorId, id) == 1;
    }

    public boolean complete(String id, String executorId, String resultData, String summary) {
        return jdbcTemplate.update("""
                update medical_order set status = 'COMPLETED', result_data = ?::jsonb, result_summary = ?, completed_at = now()
                where id = ? and status = 'IN_PROGRESS' and executor_id = ?
                """, resultData, summary, id, executorId) == 1;
    }

    private static class Mapper implements RowMapper<MedicalOrder> {
        public MedicalOrder mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new MedicalOrder(rs.getString("id"), rs.getString("appointment_id"), rs.getString("patient_id"),
                    rs.getString("patient_name"), rs.getString("ordering_doctor_id"), rs.getString("order_type"),
                    rs.getString("project_code"), rs.getString("project_name"), rs.getString("purpose"), rs.getString("body_part"),
                    rs.getBigDecimal("amount"), rs.getString("payment_status"), rs.getString("status"), rs.getString("executor_id"),
                    rs.getString("result_data"), rs.getString("result_summary"),
                    rs.getTimestamp("created_at").toLocalDateTime(),
                    rs.getTimestamp("started_at") == null ? null : rs.getTimestamp("started_at").toLocalDateTime(),
                    rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toLocalDateTime());
        }
    }
}
