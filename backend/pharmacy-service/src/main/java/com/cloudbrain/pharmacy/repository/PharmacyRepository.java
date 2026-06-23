package com.cloudbrain.pharmacy.repository;

import com.cloudbrain.pharmacy.entity.Prescription;
import com.cloudbrain.pharmacy.entity.PrescriptionItem;
import com.cloudbrain.pharmacy.entity.PrescriptionStatus;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PharmacyRepository {
    private final JdbcTemplate jdbc;

    public PharmacyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Drug> drugs(String keyword) {
        String like = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";
        return jdbc.query("""
                select c.*, i.quantity, i.warning_threshold
                from drug_catalog c join drug_inventory i on i.drug_id = c.id
                where c.enabled = true and (? is null or c.drug_name like ? or c.drug_code like ?)
                order by c.drug_code
                """, (rs, row) -> new Drug(rs.getString("id"), rs.getString("drug_code"), rs.getString("drug_name"),
                rs.getString("specification"), rs.getString("unit"), rs.getBigDecimal("unit_price"),
                rs.getInt("quantity"), rs.getInt("warning_threshold")), like, like, like);
    }

    public Drug drug(String drugId) {
        return jdbc.query("""
                select c.*, i.quantity, i.warning_threshold
                from drug_catalog c join drug_inventory i on i.drug_id = c.id
                where c.id = ? and c.enabled = true
                """, (rs, row) -> new Drug(rs.getString("id"), rs.getString("drug_code"), rs.getString("drug_name"),
                rs.getString("specification"), rs.getString("unit"), rs.getBigDecimal("unit_price"),
                rs.getInt("quantity"), rs.getInt("warning_threshold")), drugId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("药品不存在或已停用"));
    }

    public void insertPrescription(Prescription prescription) {
        jdbc.update("""
                insert into prescription
                    (id, prescription_no, appointment_id, medical_record_id, patient_id, patient_name, doctor_id,
                     diagnosis, status, total_amount, ai_assistance_id, ai_adoption_status, ai_revision_note,
                     confirmed_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                """, prescription.id(), prescription.prescriptionNo(), prescription.appointmentId(),
                prescription.medicalRecordId(), prescription.patientId(), prescription.patientName(),
                prescription.doctorId(), prescription.diagnosis(), prescription.status().name(),
                prescription.totalAmount(), prescription.aiAssistanceId(), prescription.aiAdoptionStatus(),
                prescription.aiRevisionNote());
        for (PrescriptionItem item : prescription.items()) {
            jdbc.update("""
                    insert into prescription_item
                        (id, prescription_id, drug_id, drug_name, quantity, dosage, usage, frequency,
                         days, note, unit_price, amount)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, item.id(), prescription.id(), item.drugId(), item.drugName(), item.quantity(),
                    item.dosage(), item.usage(), item.frequency(), item.days(), item.note(),
                    item.unitPrice(), item.amount());
        }
    }

    public Prescription findPrescription(String id) {
        Prescription base = jdbc.query("select * from prescription where id = ?", (rs, row) -> prescription(rs), id)
                .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("处方不存在"));
        List<PrescriptionItem> items = jdbc.query("select * from prescription_item where prescription_id = ? order by id",
                (rs, row) -> item(rs), id);
        return new Prescription(base.id(), base.prescriptionNo(), base.appointmentId(), base.medicalRecordId(),
                base.patientId(), base.patientName(), base.doctorId(), base.diagnosis(), base.status(),
                base.totalAmount(), base.paymentOrderId(), base.aiAssistanceId(), base.aiAdoptionStatus(),
                base.aiRevisionNote(), base.createdAt(), base.confirmedAt(), base.paidAt(),
                base.dispensedAt(), base.returnedAt(), base.dispensedBy(), base.returnedBy(),
                base.returnReason(), items);
    }

    public List<Prescription> list(String patientId, String status) {
        return jdbc.query("""
                select * from prescription
                where (? is null or patient_id = ?) and (? is null or status = ?)
                order by created_at desc
                """, (rs, row) -> {
            Prescription base = prescription(rs);
            return findPrescription(base.id());
        }, patientId, patientId, status, status);
    }

    public boolean markPaid(String id, String patientId, String paymentOrderId) {
        return jdbc.update("""
                update prescription
                set status = 'WAITING_DISPENSE', payment_order_id = ?, paid_at = now()
                where id = ? and patient_id = ? and status in ('CONFIRMED', 'PENDING_PAYMENT', 'PAID')
                """, paymentOrderId, id, patientId) == 1;
    }

    public boolean markDispensed(String id, String operatorId) {
        return jdbc.update("""
                update prescription
                set status = 'DISPENSED', dispensed_by = ?, dispensed_at = now()
                where id = ? and status = 'WAITING_DISPENSE'
                """, operatorId, id) == 1;
    }

    public boolean markReturned(String id, String operatorId, String reason) {
        return jdbc.update("""
                update prescription
                set status = 'RETURNED', returned_by = ?, returned_at = now(), return_reason = ?
                where id = ? and status = 'DISPENSED'
                """, operatorId, reason, id) == 1;
    }

    public StockChange deductStock(String drugId, String prescriptionId, int quantity, String operatorId) {
        int before = stock(drugId);
        int updated = jdbc.update("""
                update drug_inventory
                set quantity = quantity - ?, updated_at = now()
                where drug_id = ? and quantity >= ?
                """, quantity, drugId, quantity);
        if (updated != 1) throw new IllegalStateException("药品库存不足");
        int after = before - quantity;
        flow(drugId, prescriptionId, "OUT", quantity, before, after, operatorId, "处方发药");
        return new StockChange(before, after);
    }

    public StockChange restoreStock(String drugId, String prescriptionId, int quantity, String operatorId, String reason) {
        int before = stock(drugId);
        jdbc.update("update drug_inventory set quantity = quantity + ?, updated_at = now() where drug_id = ?",
                quantity, drugId);
        int after = before + quantity;
        flow(drugId, prescriptionId, "IN", quantity, before, after, operatorId, reason);
        return new StockChange(before, after);
    }

    private int stock(String drugId) {
        return jdbc.query("select quantity from drug_inventory where drug_id = ? for update",
                (rs, row) -> rs.getInt(1), drugId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("药品库存不存在"));
    }

    private void flow(String drugId, String prescriptionId, String direction, int quantity, int before, int after,
                      String operatorId, String reason) {
        jdbc.update("""
                insert into inventory_flow
                    (id, drug_id, prescription_id, direction, quantity, before_quantity, after_quantity, operator_id, reason)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "flow-" + UUID.randomUUID(), drugId, prescriptionId, direction, quantity,
                before, after, operatorId, reason);
    }

    private Prescription prescription(ResultSet rs) throws SQLException {
        return new Prescription(rs.getString("id"), rs.getString("prescription_no"),
                rs.getString("appointment_id"), rs.getString("medical_record_id"), rs.getString("patient_id"),
                rs.getString("patient_name"), rs.getString("doctor_id"), rs.getString("diagnosis"),
                PrescriptionStatus.valueOf(rs.getString("status")), rs.getBigDecimal("total_amount"),
                rs.getString("payment_order_id"), rs.getString("ai_assistance_id"),
                rs.getString("ai_adoption_status"), rs.getString("ai_revision_note"),
                time(rs.getTimestamp("created_at")), time(rs.getTimestamp("confirmed_at")),
                time(rs.getTimestamp("paid_at")), time(rs.getTimestamp("dispensed_at")),
                time(rs.getTimestamp("returned_at")), rs.getString("dispensed_by"),
                rs.getString("returned_by"), rs.getString("return_reason"), List.of());
    }

    private PrescriptionItem item(ResultSet rs) throws SQLException {
        return new PrescriptionItem(rs.getString("id"), rs.getString("prescription_id"),
                rs.getString("drug_id"), rs.getString("drug_name"), rs.getInt("quantity"),
                rs.getString("dosage"), rs.getString("usage"), rs.getString("frequency"),
                rs.getInt("days"), rs.getString("note"), rs.getBigDecimal("unit_price"),
                rs.getBigDecimal("amount"));
    }

    private static LocalDateTime time(java.sql.Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    public record Drug(String id, String drugCode, String drugName, String specification, String unit,
                       BigDecimal unitPrice, int quantity, int warningThreshold) {}
    public record StockChange(int beforeQuantity, int afterQuantity) {}
}
