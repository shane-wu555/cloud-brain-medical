package com.cloudbrain.medicalorder.repository;

import com.cloudbrain.medicalorder.domain.MedicalOrder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MedicalOrderRepository {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<MedicalOrder> mapper = new Mapper();

    public MedicalOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public MedicalOrder create(MedicalOrder order) {
        jdbcTemplate.update("""
                insert into medical_order
                    (id, appointment_id, patient_id, patient_name, ordering_doctor_id, order_type,
                     project_code, project_name, purpose, body_part, amount, payment_status, status, urgency)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, order.id(), order.appointmentId(), order.patientId(), order.patientName(), order.orderingDoctorId(),
                order.orderType(), order.projectCode(), order.projectName(), order.purpose(), order.bodyPart(), order.amount(),
                order.paymentStatus(), order.status(), order.urgency());
        return findById(order.id()).orElseThrow();
    }

    public List<MedicalOrder> find(String type, String status, String patientId) {
        StringBuilder sql = new StringBuilder("""
                select * from medical_order
                where 1 = 1
                """);
        List<Object> args = new ArrayList<>();

        if (type != null && !type.isBlank()) {
            sql.append(" and order_type = ?");
            args.add(type);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" and status = ?");
            args.add(status);
        }
        if (patientId != null && !patientId.isBlank()) {
            sql.append(" and patient_id = ?");
            args.add(patientId);
        }

        sql.append("""
                
                order by case when urgency = 'EMERGENCY' then 0 else 1 end,
                         queue_number nulls last, created_at
                """);
        return jdbcTemplate.query(sql.toString(), mapper, args.toArray());
    }

    public Optional<MedicalOrder> findById(String id) {
        return jdbcTemplate.query("select * from medical_order where id = ?", mapper, id).stream().findFirst();
    }

    public boolean markPaid(String id) {
        return jdbcTemplate.update("""
                update medical_order
                set payment_status = 'PAID', status = 'WAITING_TRIAGE'
                where id = ? and status = 'PENDING_PAYMENT'
                """, id) == 1;
    }

    public List<ExecutorCandidate> executorCandidates(String type, String projectCode) {
        return jdbcTemplate.query("""
                select e.id, e.name, e.specialties, e.location, e.equipment_ids, e.capacity,
                       (select count(*)
                        from medical_order o
                        where o.executor_workspace_id = e.id
                          and o.status in ('WAITING','IN_PROGRESS')) current_load
                from medical_workspace e
                where e.workspace_type = ?
                  and e.active
                  and exists (
                      select 1
                      from medical_workspace_project p
                      where p.workspace_id = e.id
                        and p.active
                        and p.project_code = ?
                  )
                order by e.id
                """, (rs, row) -> new ExecutorCandidate(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getInt(6), rs.getInt(7)), type, projectCode);
    }

    public Optional<MedicalTechnician> technician(String userId) {
        return jdbcTemplate.query("""
                select id, employee_no, name, role_type, workspace_id
                from medical_technician
                where id = ? and active
                """, (rs, row) -> new MedicalTechnician(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5)), userId).stream().findFirst();
    }

    public boolean assign(String id, String workspaceId, String workspaceName, String location, String equipmentId,
            String createdBy, String reasons) {
        jdbcTemplate.query("select pg_advisory_xact_lock(hashtext(?))", rs -> null, "medical-order:" + workspaceId);
        String urgency = jdbcTemplate.queryForObject("select urgency from medical_order where id = ?", String.class, id);
        Integer next = "EMERGENCY".equals(urgency)
                ? jdbcTemplate.queryForObject("""
                        select coalesce(min(queue_number), 0) - 1
                        from medical_order
                        where executor_workspace_id = ?
                          and status in ('WAITING','IN_PROGRESS')
                        """, Integer.class, workspaceId)
                : jdbcTemplate.queryForObject("""
                        select coalesce(max(queue_number), 0) + 1
                        from medical_order
                        where executor_workspace_id = ?
                          and status in ('WAITING','IN_PROGRESS')
                        """, Integer.class, workspaceId);
        return jdbcTemplate.update("""
                update medical_order
                set executor_workspace_id = ?,
                    executor_workspace_name = ?,
                    executor_workspace_location = ?,
                    executor_id = ?,
                    executor_name = ?,
                    execution_location = ?,
                    equipment_id = ?,
                    queue_number = ?,
                    triage_created_by_type = ?,
                    triage_reasons = ?,
                    status = 'WAITING'
                where id = ? and status = 'WAITING_TRIAGE'
                """, workspaceId, workspaceName, location, workspaceId, workspaceName, location, equipmentId,
                next, createdBy, reasons, id) == 1;
    }

    public boolean start(String id, String workspaceId, String doctorId, String doctorName) {
        return jdbcTemplate.update("""
                update medical_order
                set status = 'IN_PROGRESS',
                    started_at = now(),
                    executing_doctor_id = ?,
                    executing_doctor_name = ?
                where id = ?
                  and status = 'WAITING'
                  and executor_workspace_id = ?
                """, doctorId, doctorName, id, workspaceId) == 1;
    }

    public void moveToTail(String id, String workspaceId) {
        jdbcTemplate.query("select pg_advisory_xact_lock(hashtext(?))", rs -> null, "medical-order:" + workspaceId);
        Integer next = jdbcTemplate.queryForObject("""
                select coalesce(max(queue_number), 0) + 1
                from medical_order
                where executor_workspace_id = ?
                """, Integer.class, workspaceId);
        if (jdbcTemplate.update("""
                update medical_order
                set queue_number = ?, missed_count = missed_count + 1
                where id = ?
                  and executor_workspace_id = ?
                  and status = 'WAITING'
                """, next, id, workspaceId) != 1) {
            throw new IllegalStateException("只有待执行医技单可以标记过号");
        }
    }

    public boolean complete(String id, String workspaceId, String doctorId, String resultData, String summary, String sourceType, String aiRecordId) {
        return jdbcTemplate.update("""
                update medical_order
                set status = 'COMPLETED',
                    result_data = ?::jsonb,
                    result_summary = ?,
                    result_created_by_type = ?,
                    result_ai_record_id = ?,
                    result_confirmed_by = ?,
                    result_confirmed_at = now(),
                    completed_at = now()
                where id = ?
                  and status = 'IN_PROGRESS'
                  and executor_workspace_id = ?
                """, resultData, summary, sourceType, aiRecordId, doctorId, id, workspaceId) == 1;
    }

    private static class Mapper implements RowMapper<MedicalOrder> {
        public MedicalOrder mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new MedicalOrder(rs.getString("id"), rs.getString("appointment_id"), rs.getString("patient_id"),
                    rs.getString("patient_name"), rs.getString("ordering_doctor_id"), rs.getString("order_type"),
                    rs.getString("project_code"), rs.getString("project_name"), rs.getString("purpose"), rs.getString("body_part"),
                    rs.getBigDecimal("amount"), rs.getString("payment_status"), rs.getString("status"),
                    rs.getString("executor_workspace_id"), rs.getString("executor_workspace_name"),
                    rs.getString("executor_workspace_location"), rs.getString("equipment_id"),
                    (Integer) rs.getObject("queue_number"), rs.getString("urgency"), rs.getString("triage_created_by_type"),
                    rs.getString("triage_reasons"), rs.getInt("missed_count"),
                    rs.getString("result_data"), rs.getString("result_summary"),
                    rs.getTimestamp("created_at").toLocalDateTime(),
                    rs.getTimestamp("started_at") == null ? null : rs.getTimestamp("started_at").toLocalDateTime(),
                    rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toLocalDateTime());
        }
    }

    public record ExecutorCandidate(String id, String name, String specialties, String location, String equipmentIds,
            int capacity, int currentLoad) {
    }

    public record MedicalTechnician(String id, String employeeNo, String name, String roleType, String workspaceId) {
    }
}
