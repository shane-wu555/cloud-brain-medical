package com.cloudbrain.pharmacy.repository;

import com.cloudbrain.pharmacy.entity.Prescription;
import com.cloudbrain.pharmacy.entity.PrescriptionItem;
import com.cloudbrain.pharmacy.entity.PrescriptionStatus;
import com.cloudbrain.pharmacy.entity.DrugReturnItem;
import com.cloudbrain.pharmacy.entity.DrugReturnOrder;
import com.cloudbrain.pharmacy.entity.DrugReturnStatus;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PharmacyRepository {
    private final JdbcTemplate jdbc;

    public PharmacyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Drug> drugs(String keyword, String storageCondition) {
        String like = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim().toLowerCase() + "%";
        StringBuilder sql = new StringBuilder("""
                select d.*, s.quantity, s.warning_threshold
                from drug d
                join drug_stock s on s.drug_id = d.id
                where d.active = true
                """);
        List<Object> args = new ArrayList<>();
        if (like != null) {
            sql.append("""
                    and (
                        lower(d.drug_name) like ?
                        or lower(d.code) like ?
                        or lower(d.specification) like ?
                        or lower(d.dosage_form) like ?
                        or lower(d.storage_condition) like ?
                    )
                    """);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (storageCondition != null && !storageCondition.isBlank()) {
            sql.append(" and d.storage_condition = ?");
            args.add(storageCondition.trim());
        }
        sql.append(" order by case when s.quantity <= s.warning_threshold then 0 else 1 end, d.dosage_form, d.drug_name, d.code");
        return jdbc.query(sql.toString(), (rs, row) -> drug(rs), args.toArray());
    }

    public List<Drug> drugsByIds(List<String> drugIds) {
        List<String> ids = drugIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = ids.stream()
                .map(id -> "?::uuid")
                .collect(java.util.stream.Collectors.joining(","));
        List<Drug> rows = jdbc.query("""
                select d.*, s.quantity, s.warning_threshold
                from drug d
                join drug_stock s on s.drug_id = d.id
                where d.id in (""" + placeholders + """
                ) and d.active = true
                """, (rs, row) -> drug(rs), ids.toArray());
        Map<String, Drug> byId = new java.util.LinkedHashMap<>();
        rows.forEach(drug -> byId.put(drug.id(), drug));
        return ids.stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public Drug drug(String drugId) {
        return jdbc.query("""
                select d.*, s.quantity, s.warning_threshold
                from drug d
                join drug_stock s on s.drug_id = d.id
                where d.id = ?::uuid and d.active = true
                """, (rs, row) -> drug(rs), drugId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("drug does not exist or is inactive"));
    }

    public List<DrugDemandObservation> drugDemandObservations(int lookbackDays) {
        return jdbc.query("""
                select d.id as drug_id,
                       d.code as drug_code,
                       d.drug_name,
                       d.unit_price,
                       s.quantity,
                       s.warning_threshold,
                       day.demand_date::date as demand_date,
                       coalesce(sum(f.quantity), 0)::int as dispensed_quantity
                from drug d
                join drug_stock s on s.drug_id = d.id
                cross join generate_series(
                    current_date - (?::int - 1),
                    current_date,
                    interval '1 day'
                ) as day(demand_date)
                left join stock_flow f on f.drug_id = d.id
                    and f.direction = 'OUT'
                    and f.created_at::date = day.demand_date::date
                where d.active = true
                group by d.id, d.code, d.drug_name, d.unit_price, s.quantity,
                         s.warning_threshold, day.demand_date
                order by d.code, day.demand_date
                """, (rs, row) -> demandObservation(rs), Math.max(1, lookbackDays));
    }

    public int updateWarningThreshold(String drugId, int warningThreshold) {
        return jdbc.update("""
                update drug_stock
                set warning_threshold = ?
                where drug_id = ?::uuid
                """, Math.max(0, warningThreshold), drugId);
    }

    public void insertPrescription(Prescription prescription) {
        jdbc.update("""
                insert into prescription
                    (id, prescription_no, appointment_id, medical_record_id, patient_id, patient_name,
                     doctor_id, doctor_name, diagnosis, status, total_amount, ai_record_id, ai_adoption_status)
                values (?::uuid, ?, ?::uuid, ?, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?)
                """, prescription.id(), prescription.prescriptionNo(), prescription.appointmentId(),
                prescription.medicalRecordId(), prescription.patientId(), prescription.patientName(),
                prescription.doctorId(), prescription.doctorId(), prescription.diagnosis(),
                prescription.status().name(), prescription.totalAmount(),
                prescription.aiAssistanceId(), prescription.aiAdoptionStatus());
        for (PrescriptionItem item : prescription.items()) {
            jdbc.update("""
                    insert into prescription_item
                        (id, prescription_id, drug_id, drug_name, quantity, dosage, usage,
                         frequency, days, unit_price, amount)
                    values (?::uuid, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, item.id(), prescription.id(), item.drugId(), item.drugName(),
                    item.quantity(), item.dosage(), item.usage(), item.frequency(), item.days(),
                    item.unitPrice(), item.amount());
        }
    }

    public Prescription findPrescription(String id) {
        Prescription base = jdbc.query("select * from prescription where id = ?::uuid",
                        (rs, row) -> prescription(rs), id)
                .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("prescription does not exist"));
        List<PrescriptionItem> items = jdbc.query("""
                select * from prescription_item
                where prescription_id = ?::uuid
                order by id
                """, (rs, row) -> item(rs), id);
        return new Prescription(base.id(), base.prescriptionNo(), base.appointmentId(), base.medicalRecordId(),
                base.patientId(), base.patientName(), base.doctorId(), base.diagnosis(), base.status(),
                base.totalAmount(), base.paymentOrderId(), base.aiAssistanceId(), base.aiAdoptionStatus(),
                base.aiRevisionNote(), base.createdAt(), base.confirmedAt(), base.paidAt(),
                base.dispensedAt(), base.returnedAt(), base.dispensedBy(), base.returnedBy(),
                base.returnReason(), items);
    }

    public List<Prescription> list(String patientId, String status) {
        StringBuilder sql = new StringBuilder("select * from prescription where 1 = 1");
        List<Object> args = new ArrayList<>();
        if (patientId != null && !patientId.isBlank()) {
            sql.append(" and patient_id = ?::uuid");
            args.add(patientId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" and status = ?");
            args.add(status);
        }
        sql.append(" order by created_at desc");
        return jdbc.query(sql.toString(), (rs, row) -> findPrescription(rs.getString("id")), args.toArray());
    }

    public List<Prescription> listByStatuses(String patientId, List<PrescriptionStatus> statuses) {
        StringBuilder sql = new StringBuilder("select * from prescription where 1 = 1");
        List<Object> args = new ArrayList<>();
        if (patientId != null && !patientId.isBlank()) {
            sql.append(" and patient_id = ?::uuid");
            args.add(patientId);
        }
        if (statuses != null && !statuses.isEmpty()) {
            sql.append(" and status in (");
            sql.append("?,".repeat(statuses.size()));
            sql.setLength(sql.length() - 1);
            sql.append(")");
            statuses.forEach(status -> args.add(status.name()));
        }
        sql.append(" order by created_at desc");
        return jdbc.query(sql.toString(), (rs, row) -> findPrescription(rs.getString("id")), args.toArray());
    }

    public boolean markPaid(String id, String patientId, String paymentOrderId) {
        return jdbc.update("""
                update prescription
                set status = 'WAITING_DISPENSE'
                where id = ?::uuid and patient_id = ?::uuid and status in ('CONFIRMED', 'PENDING_PAYMENT', 'PAID')
                """, id, patientId) == 1;
    }

    public boolean markDispensed(String id, String operatorId) {
        return jdbc.update("""
                update prescription
                set status = 'DISPENSED', dispensed_by = ?, dispensed_at = now()
                where id = ?::uuid and status = 'WAITING_DISPENSE'
                """, operatorId, id) == 1;
    }

    public boolean markReturned(String id, String operatorId, String reason) {
        return jdbc.update("""
                update prescription
                set status = 'RETURNED', returned_by = ?, returned_at = now()
                where id = ?::uuid and status = 'DISPENSED'
                """, operatorId, id) == 1;
    }

    public boolean markReturnedBeforeDispense(String id, String operatorId, String reason, PrescriptionStatus returnStatus) {
        return jdbc.update("""
                update prescription
                set status = ?, returned_by = ?, returned_at = now(), updated_at = now()
                where id = ?::uuid and status in ('CONFIRMED','PENDING_PAYMENT','PAID','WAITING_DISPENSE')
                """, returnStatus.name(), operatorId, id) == 1;
    }

    public StockChange deductStock(String drugId, String prescriptionId, int quantity, String operatorId) {
        int before = stock(drugId);
        int updated = jdbc.update("""
                update drug_stock
                set quantity = quantity - ?
                where drug_id = ?::uuid and quantity >= ?
                """, quantity, drugId, quantity);
        if (updated != 1) throw new IllegalStateException("insufficient drug stock");
        int after = before - quantity;
        flow(drugId, prescriptionId, "OUT", quantity, before, after, operatorId, "prescription dispense");
        return new StockChange(before, after);
    }

    public StockChange restoreStock(String drugId, String prescriptionId, int quantity, String operatorId, String reason) {
        int before = stock(drugId);
        jdbc.update("update drug_stock set quantity = quantity + ? where drug_id = ?::uuid", quantity, drugId);
        int after = before + quantity;
        flow(drugId, prescriptionId, "IN", quantity, before, after, operatorId, reason);
        return new StockChange(before, after);
    }

    public StockChange addStock(String drugId, int quantity, String operatorId, String reason) {
        int before = stock(drugId);
        jdbc.update("update drug_stock set quantity = quantity + ? where drug_id = ?::uuid", quantity, drugId);
        int after = before + quantity;
        flow(drugId, null, "IN", quantity, before, after, operatorId, reason);
        return new StockChange(before, after);
    }

    public DrugReturnOrder createDrugReturn(Prescription prescription, String doctorId, String doctorOpinion,
            String opinionTemplate, DrugReturnStatus status) {
        String id = UUID.randomUUID().toString();
        BigDecimal total = prescription.items().stream()
                .map(PrescriptionItem::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String returnNo = "RT" + System.currentTimeMillis();
        jdbc.update("""
                insert into drug_return_request
                    (id, return_no, prescription_id, patient_id, patient_name, doctor_id,
                     doctor_opinion, opinion_template, status, total_amount)
                values (?::uuid, ?, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?)
                """, id, returnNo, prescription.id(), prescription.patientId(), prescription.patientName(),
                doctorId, doctorOpinion, opinionTemplate, status.name(), total);
        return findDrugReturn(id);
    }

    public DrugReturnOrder findDrugReturn(String id) {
        DrugReturnOrder base = jdbc.query("""
                        select r.*, p.prescription_no
                        from drug_return_request r
                        join prescription p on p.id = r.prescription_id
                        where r.id = ?::uuid
                        """, (rs, row) -> drugReturn(rs), id)
                .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("drug return does not exist"));
        return drugReturnWithItems(base);
    }

    public List<DrugReturnOrder> listDrugReturns(String patientId, String status) {
        StringBuilder sql = new StringBuilder("""
                select r.*, p.prescription_no
                from drug_return_request r
                join prescription p on p.id = r.prescription_id
                where 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (patientId != null && !patientId.isBlank()) {
            sql.append(" and r.patient_id = ?::uuid");
            args.add(patientId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" and r.status = ?");
            args.add(status);
        }
        sql.append(" order by r.created_at desc");
        return jdbc.query(sql.toString(), (rs, row) -> drugReturnWithItems(drugReturn(rs)), args.toArray());
    }

    public boolean completeDrugReturn(String id, String cashierId, String refundOrderId) {
        return jdbc.update("""
                update drug_return_request
                set status = 'RETURN_REFUNDED', cashier_id = ?, refund_order_id = ?,
                    completed_at = now(), updated_at = now()
                where id = ?::uuid and status = 'RETURN_PENDING_REFUND'
                """, cashierId, refundOrderId, id) == 1;
    }

    public boolean markReturnRefunded(String id) {
        return jdbc.update("""
                update prescription
                set status = 'RETURN_REFUNDED', updated_at = now()
                where id = ?::uuid and status = 'RETURN_PENDING_REFUND'
                """, id) == 1;
    }

    private int stock(String drugId) {
        return jdbc.query("select quantity from drug_stock where drug_id = ?::uuid for update",
                        (rs, row) -> rs.getInt(1), drugId)
                .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("drug stock does not exist"));
    }

    private void flow(String drugId, String prescriptionId, String direction, int quantity, int before, int after,
            String operatorId, String reason) {
        jdbc.update("""
                insert into stock_flow
                    (id, drug_id, prescription_id, direction, quantity, stock_before, stock_after, operator_id, reason)
                values (?::uuid, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), drugId, prescriptionId, direction, quantity,
                before, after, operatorId, reason);
    }

    private Drug drug(ResultSet rs) throws SQLException {
        return new Drug(rs.getString("id"), rs.getString("code"), rs.getString("drug_name"),
                rs.getString("specification"), rs.getString("unit"), rs.getBigDecimal("unit_price"),
                rs.getString("dosage_form"), rs.getString("storage_condition"),
                rs.getInt("quantity"), rs.getInt("warning_threshold"));
    }

    private DrugDemandObservation demandObservation(ResultSet rs) throws SQLException {
        return new DrugDemandObservation(rs.getString("drug_id"), rs.getString("drug_code"),
                rs.getString("drug_name"), rs.getBigDecimal("unit_price"), rs.getInt("quantity"),
                rs.getInt("warning_threshold"), rs.getObject("demand_date", LocalDate.class),
                rs.getInt("dispensed_quantity"));
    }

    private Prescription prescription(ResultSet rs) throws SQLException {
        return new Prescription(rs.getString("id"), rs.getString("prescription_no"),
                rs.getString("appointment_id"), rs.getString("medical_record_id"), rs.getString("patient_id"),
                rs.getString("patient_name"), rs.getString("doctor_id"), rs.getString("diagnosis"),
                PrescriptionStatus.valueOf(rs.getString("status")), rs.getBigDecimal("total_amount"),
                null, rs.getString("ai_record_id"), rs.getString("ai_adoption_status"), null,
                time(rs.getTimestamp("created_at")), null, null,
                time(rs.getTimestamp("dispensed_at")), time(rs.getTimestamp("returned_at")),
                rs.getString("dispensed_by"), rs.getString("returned_by"), null, List.of());
    }

    private PrescriptionItem item(ResultSet rs) throws SQLException {
        return new PrescriptionItem(rs.getString("id"), rs.getString("prescription_id"),
                rs.getString("drug_id"), rs.getString("drug_name"), rs.getInt("quantity"),
                rs.getString("dosage"), rs.getString("usage"), rs.getString("frequency"),
                rs.getInt("days"), null, rs.getBigDecimal("unit_price"), rs.getBigDecimal("amount"));
    }

    private DrugReturnOrder drugReturn(ResultSet rs) throws SQLException {
        return new DrugReturnOrder(rs.getString("id"), rs.getString("return_no"),
                rs.getString("prescription_id"), rs.getString("prescription_no"),
                rs.getString("patient_id"), rs.getString("patient_name"), rs.getString("doctor_id"),
                rs.getString("doctor_opinion"), rs.getString("opinion_template"),
                DrugReturnStatus.valueOf(rs.getString("status")), rs.getBigDecimal("total_amount"),
                string(rs, "pharmacist_id"), string(rs, "pharmacist_opinion"),
                string(rs, "cashier_id"), string(rs, "refund_order_id"),
                time(rs, "created_at"), time(rs, "verified_at"),
                time(rs, "completed_at"), List.of());
    }

    private DrugReturnOrder drugReturnWithItems(DrugReturnOrder base) {
        List<DrugReturnItem> items = jdbc.query("""
                select * from prescription_item
                where prescription_id = ?::uuid
                order by id
                """, (rs, row) -> prescriptionItemAsReturnItem(rs, base.id()), base.prescriptionId());
        return new DrugReturnOrder(base.id(), base.returnNo(), base.prescriptionId(), base.prescriptionNo(),
                base.patientId(), base.patientName(), base.doctorId(), base.doctorOpinion(), base.opinionTemplate(),
                base.status(), base.totalAmount(), base.pharmacistId(), base.pharmacistOpinion(),
                base.cashierId(), base.refundOrderId(), base.createdAt(), base.verifiedAt(),
                base.completedAt(), items);
    }

    private DrugReturnItem prescriptionItemAsReturnItem(ResultSet rs, String returnId) throws SQLException {
        return new DrugReturnItem(rs.getString("id"), returnId, rs.getString("id"),
                rs.getString("drug_id"), rs.getString("drug_name"), rs.getInt("quantity"),
                rs.getBigDecimal("unit_price"), rs.getBigDecimal("amount"),
                null, null, null, null, null, null);
    }

    private static LocalDateTime time(java.sql.Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private static LocalDateTime time(ResultSet rs, String column) throws SQLException {
        return hasColumn(rs, column) ? time(rs.getTimestamp(column)) : null;
    }

    private static String string(ResultSet rs, String column) throws SQLException {
        return hasColumn(rs, column) ? rs.getString(column) : null;
    }

    private static boolean hasColumn(ResultSet rs, String column) throws SQLException {
        var metadata = rs.getMetaData();
        for (int index = 1; index <= metadata.getColumnCount(); index += 1) {
            if (column.equalsIgnoreCase(metadata.getColumnLabel(index)) || column.equalsIgnoreCase(metadata.getColumnName(index))) {
                return true;
            }
        }
        return false;
    }

    public record Drug(String id, String drugCode, String drugName, String specification, String unit,
            BigDecimal unitPrice, String dosageForm, String storageCondition,
            int quantity, int warningThreshold) {}
    public record DrugDemandObservation(String drugId, String drugCode, String drugName, BigDecimal unitPrice,
            int quantity, int warningThreshold, LocalDate demandDate, int dispensedQuantity) {}
    public record StockChange(int beforeQuantity, int afterQuantity) {}
}
