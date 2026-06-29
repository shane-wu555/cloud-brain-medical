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
                     item_code, item_name, purpose, body_part, amount, payment_status, status, urgency)
                values (?::uuid, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                order.id(), order.appointmentId(), order.patientId(), order.patientName(),
                order.orderingDoctorId(), order.orderType(),
                order.itemCode(), order.itemName(), order.purpose(), order.bodyPart(),
                order.amount(), order.paymentStatus(), order.status(), order.urgency());
        return findById(order.id()).orElseThrow();
    }

    public boolean existsActiveOrder(String appointmentId, String itemCode) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from medical_order where appointment_id = ?::uuid and item_code = ? and status not in ('CANCELLED')",
                Integer.class, appointmentId, itemCode);
        return count != null && count > 0;
    }

    public List<MedicalOrder> find(String type, String status, String patientId, String appointmentId) {
        StringBuilder sql = new StringBuilder("""
                select mo.*,
                       er.name     as room_name,
                       er.location as room_location
                from medical_order mo
                left join examination_room er on er.id = mo.room_id
                where 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (type != null && !type.isBlank())            { sql.append(" and mo.order_type = ?");      args.add(type); }
        if (status != null && !status.isBlank())        { sql.append(" and mo.status = ?");           args.add(status); }
        if (patientId != null && !patientId.isBlank())  { sql.append(" and mo.patient_id = ?::uuid");       args.add(patientId); }
        if (appointmentId != null && !appointmentId.isBlank()) { sql.append(" and mo.appointment_id = ?::uuid"); args.add(appointmentId); }
        sql.append("""
                 order by case when mo.urgency = 'EMERGENCY' then 0 else 1 end,
                          mo.queue_number nulls last, mo.created_at
                """);
        return jdbcTemplate.query(sql.toString(), mapper, args.toArray());
    }

    public Optional<MedicalOrder> findById(String id) {
        return jdbcTemplate.query("""
                select mo.*, er.name as room_name, er.location as room_location
                from medical_order mo
                left join examination_room er on er.id = mo.room_id
                where mo.id = ?::uuid
                """, mapper, id).stream().findFirst();
    }

    public boolean markPaid(String id) {
        return jdbcTemplate.update("""
                update medical_order
                set payment_status = 'PAID', status = 'WAITING_TRIAGE'
                where id = ?::uuid and status = 'PENDING_PAYMENT'
                """, id) == 1;
    }

    public List<RoomCandidate> roomCandidates(String roomType, String itemCode) {
        return jdbcTemplate.query("""
                select r.id,
                       r.name,
                       string_agg(distinct p.item_name, ',') as item_names,
                       r.location,
                       r.equipment_ids,
                       r.capacity,
                       (select count(*)
                        from medical_order o
                        where o.room_id = r.id
                          and o.status in ('WAITING', 'IN_PROGRESS')) as current_load
                from examination_room r
                join room_item_capability p on p.room_id = r.id and p.active
                where r.room_type = ?
                  and r.active
                  and exists (
                      select 1 from room_item_capability c
                      where c.room_id = r.id and c.active and c.item_code = ?
                  )
                group by r.id, r.name, r.location, r.equipment_ids, r.capacity
                order by r.id
                """,
                (rs, row) -> new RoomCandidate(
                        rs.getString("id"), rs.getString("name"), rs.getString("item_names"),
                        rs.getString("location"), rs.getString("equipment_ids"),
                        rs.getInt("capacity"), rs.getInt("current_load")),
                roomType, itemCode);
    }

    public Optional<StaffRoom> staffRoom(String staffId) {
        return jdbcTemplate.query("""
                select staff_id, room_id
                from staff_room_assignment
                where staff_id = ? and active
                """,
                (rs, row) -> new StaffRoom(rs.getString("staff_id"), rs.getString("room_id")),
                staffId).stream().findFirst();
    }

    public boolean assign(String id, String roomId, String triageSource, String reasons) {
        jdbcTemplate.query("select pg_advisory_xact_lock(hashtext(?))", rs -> null, "medical-order:" + roomId);
        String urgency = jdbcTemplate.queryForObject("select urgency from medical_order where id = ?::uuid", String.class, id);
        Integer next = "EMERGENCY".equals(urgency)
                ? jdbcTemplate.queryForObject("""
                        select coalesce(min(queue_number), 0) - 1
                        from medical_order
                        where room_id = ? and status in ('WAITING', 'IN_PROGRESS')
                        """, Integer.class, roomId)
                : jdbcTemplate.queryForObject("""
                        select coalesce(max(queue_number), 0) + 1
                        from medical_order
                        where room_id = ? and status in ('WAITING', 'IN_PROGRESS')
                        """, Integer.class, roomId);
        return jdbcTemplate.update("""
                update medical_order
                set room_id = ?, queue_number = ?, triage_source = ?, triage_reasons = ?, status = 'WAITING'
                where id = ?::uuid and status = 'WAITING_TRIAGE'
                """, roomId, next, triageSource, reasons, id) == 1;
    }

    public boolean call(String id, String roomId) {
        return jdbcTemplate.update("""
                update medical_order
                set status = 'CALLED'
                where id = ?::uuid and status = 'WAITING' and room_id = ?
                """, id, roomId) == 1;
    }

    public boolean start(String id, String roomId, String staffId) {
        return jdbcTemplate.update("""
                update medical_order
                set status = 'IN_PROGRESS', started_at = now(), executing_staff_id = ?
                where id = ?::uuid and status in ('WAITING','CALLED') and room_id = ?
                """, staffId, id, roomId) == 1;
    }

    public void moveToTail(String id, String roomId) {
        jdbcTemplate.query("select pg_advisory_xact_lock(hashtext(?))", rs -> null, "medical-order:" + roomId);
        Integer next = jdbcTemplate.queryForObject("""
                select coalesce(max(queue_number), 0) + 1
                from medical_order where room_id = ?
                """, Integer.class, roomId);
        if (jdbcTemplate.update("""
                update medical_order
                set queue_number = ?, missed_count = missed_count + 1, status = 'WAITING'
                where id = ?::uuid and room_id = ? and status in ('WAITING','CALLED')
                """, next, id, roomId) != 1) {
            throw new IllegalStateException("只有待执行医技单可以标记过号");
        }
    }

    public boolean complete(String id, String roomId, String staffId,
            String summary, String sourceType, String aiRecordId) {
        return jdbcTemplate.update("""
                update medical_order
                set status = 'COMPLETED',
                    result_summary = ?,
                    result_created_by_type = ?,
                    result_ai_record_id = ?,
                    result_confirmed_by = ?,
                    result_confirmed_at = now(),
                    completed_at = now()
                where id = ?::uuid and status = 'IN_PROGRESS' and room_id = ?
                """, summary, sourceType, aiRecordId, staffId, id, roomId) == 1;
    }

    private static class Mapper implements RowMapper<MedicalOrder> {
        public MedicalOrder mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new MedicalOrder(
                    rs.getString("id"), rs.getString("appointment_id"),
                    rs.getString("patient_id"), rs.getString("patient_name"),
                    rs.getString("ordering_doctor_id"),
                    rs.getString("order_type"), rs.getString("item_code"), rs.getString("item_name"),
                    rs.getString("purpose"), rs.getString("body_part"),
                    rs.getBigDecimal("amount"), rs.getString("payment_status"), rs.getString("status"),
                    rs.getString("room_id"), rs.getString("room_name"), rs.getString("room_location"),
                    rs.getString("executing_staff_id"),
                    (Integer) rs.getObject("queue_number"), rs.getString("urgency"),
                    rs.getString("triage_source"), rs.getString("triage_reasons"), rs.getInt("missed_count"),
                    rs.getString("result_summary"), rs.getString("result_created_by_type"),
                    rs.getString("result_ai_record_id"), rs.getString("result_confirmed_by"),
                    rs.getTimestamp("result_confirmed_at") == null ? null
                            : rs.getTimestamp("result_confirmed_at").toLocalDateTime(),
                    rs.getTimestamp("created_at").toLocalDateTime(),
                    rs.getTimestamp("started_at") == null ? null
                            : rs.getTimestamp("started_at").toLocalDateTime(),
                    rs.getTimestamp("completed_at") == null ? null
                            : rs.getTimestamp("completed_at").toLocalDateTime());
        }
    }

    public record RoomCandidate(String id, String name, String itemNames, String location,
            String equipmentIds, int capacity, int currentLoad) {}

    public record StaffRoom(String staffId, String roomId) {}
}
