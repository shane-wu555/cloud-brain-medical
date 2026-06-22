package com.cloudbrain.medicalorder.repository;

import com.cloudbrain.medicalorder.domain.LaboratoryResultItem;
import com.cloudbrain.medicalorder.domain.Specimen;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LaboratoryWorkflowRepository {
    private final JdbcTemplate jdbc;

    public LaboratoryWorkflowRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean isLabOrder(String orderId) {
        Integer count = jdbc.queryForObject("select count(*) from medical_order where id = ? and order_type = 'LAB'", Integer.class, orderId);
        return count != null && count == 1;
    }

    public Specimen createSpecimen(String orderId, String type, String barcode) {
        String id = "specimen-" + UUID.randomUUID();
        jdbc.update("insert into specimen(id, medical_order_id, specimen_type, barcode) values (?, ?, ?, ?)",
                id, orderId, type, barcode);
        return specimen(id).orElseThrow();
    }

    public Optional<Specimen> specimen(String id) {
        return jdbc.query("select * from specimen where id = ?", (rs, n) -> new Specimen(
                rs.getString("id"), rs.getString("medical_order_id"), rs.getString("specimen_type"),
                rs.getString("barcode"), rs.getString("status"), rs.getString("collector_id"),
                time(rs.getTimestamp("collected_at")), time(rs.getTimestamp("received_at")),
                time(rs.getTimestamp("analyzing_at")), time(rs.getTimestamp("reviewed_at")),
                time(rs.getTimestamp("completed_at")), time(rs.getTimestamp("discarded_at")),
                rs.getString("discard_reason"), time(rs.getTimestamp("created_at"))), id).stream().findFirst();
    }

    public List<Specimen> specimens(String orderId) {
        return jdbc.query("select * from specimen where medical_order_id = ? order by created_at", (rs, n) -> new Specimen(
                rs.getString("id"), rs.getString("medical_order_id"), rs.getString("specimen_type"),
                rs.getString("barcode"), rs.getString("status"), rs.getString("collector_id"),
                time(rs.getTimestamp("collected_at")), time(rs.getTimestamp("received_at")),
                time(rs.getTimestamp("analyzing_at")), time(rs.getTimestamp("reviewed_at")),
                time(rs.getTimestamp("completed_at")), time(rs.getTimestamp("discarded_at")),
                rs.getString("discard_reason"), time(rs.getTimestamp("created_at"))), orderId);
    }

    public boolean transition(String id, String current, String next, String actorId, String reason) {
        String timestampColumn = switch (next) {
            case "COLLECTED" -> "collected_at";
            case "RECEIVED" -> "received_at";
            case "ANALYZING" -> "analyzing_at";
            case "REVIEWED" -> "reviewed_at";
            case "EXHAUSTED" -> "completed_at";
            case "DISCARDED" -> "discarded_at";
            default -> throw new IllegalArgumentException("不支持的样本状态");
        };
        String sql = "update specimen set status = ?, " + timestampColumn + " = now(), "
                + (next.equals("COLLECTED") ? "collector_id = ?, " : "collector_id = coalesce(collector_id, ?), ")
                + "discard_reason = case when ? = 'DISCARDED' then ? else discard_reason end where id = ? and status = ?";
        return jdbc.update(sql, next, actorId, next, reason, id, current) == 1;
    }

    public LaboratoryResultItem upsertResult(String orderId, String specimenId, String itemCode, String itemName,
                                             String value, String unit, String referenceRange, String abnormalFlag,
                                             String sourceType, String aiRecordId, String confirmerId) {
        String id = "lab-item-" + UUID.randomUUID();
        jdbc.update("""
                insert into laboratory_result_item
                    (id, medical_order_id, specimen_id, item_code, item_name, result_value, unit,
                     reference_range, abnormal_flag, created_by_type, ai_record_id, confirmed_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (specimen_id, item_code) do update set
                    item_name = excluded.item_name, result_value = excluded.result_value, unit = excluded.unit,
                    reference_range = excluded.reference_range, abnormal_flag = excluded.abnormal_flag,
                    created_by_type = excluded.created_by_type, ai_record_id = excluded.ai_record_id,
                    confirmed_by = excluded.confirmed_by, confirmed_at = now()
                """, id, orderId, specimenId, itemCode, itemName, value, unit, referenceRange,
                abnormalFlag, sourceType, aiRecordId, confirmerId);
        return results(orderId).stream().filter(item -> item.specimenId().equals(specimenId) && item.itemCode().equals(itemCode)).findFirst().orElseThrow();
    }

    public List<LaboratoryResultItem> results(String orderId) {
        return jdbc.query("select * from laboratory_result_item where medical_order_id = ? order by item_code", (rs, n) -> new LaboratoryResultItem(
                rs.getString("id"), rs.getString("medical_order_id"), rs.getString("specimen_id"),
                rs.getString("item_code"), rs.getString("item_name"), rs.getString("result_value"),
                rs.getString("unit"), rs.getString("reference_range"), rs.getString("abnormal_flag"),
                rs.getString("created_by_type"), rs.getString("ai_record_id"), rs.getString("confirmed_by"),
                time(rs.getTimestamp("confirmed_at")), time(rs.getTimestamp("created_at"))), orderId);
    }

    private static LocalDateTime time(Timestamp value) { return value == null ? null : value.toLocalDateTime(); }
}
