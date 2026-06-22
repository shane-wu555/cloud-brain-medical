package com.cloudbrain.cashier.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CashierRepository {
    private final JdbcTemplate jdbc;

    public CashierRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void recordPayment(
            String type, String businessId, String patientId,
            BigDecimal amount, String method, String operatorId) {
        jdbc.update("""
                insert into payment_order
                    (id, business_type, business_id, patient_id, amount, payment_method, status, operator_id, paid_at)
                values (?, ?, ?, ?, ?, ?, 'PAID', ?, now())
                on conflict (business_type, business_id) do nothing
                """, "pay-" + UUID.randomUUID(), type, businessId, patientId, amount, method, operatorId);
    }

    public void recordRefund(
            String type, String businessId, String patientId,
            BigDecimal amount, String reason, String operatorId) {
        jdbc.update("""
                insert into refund_order
                    (id, business_type, business_id, patient_id, amount, reason, status, operator_id, refunded_at)
                values (?, ?, ?, ?, ?, ?, 'REFUNDED', ?, now())
                on conflict (business_type, business_id) do nothing
                """, "refund-" + UUID.randomUUID(), type, businessId, patientId, amount, reason, operatorId);
    }

    public Payment recordTestPayment(String businessType, String businessId, String patientId,
                                     String channel, String operatorId, String channelTradeNo) {
        jdbc.update("""
                insert into payment_order
                    (id, business_type, business_id, patient_id, amount, payment_method, status,
                     operator_id, paid_at, payment_scene, channel_trade_no, callback_received_at)
                values (?, ?, ?, ?, 0.01, ?, 'PAID', ?, now(), 'SANDBOX_0_01', ?, now())
                on conflict (business_type, business_id) do nothing
                """, "pay-test-" + UUID.randomUUID(), businessType, businessId, patientId,
                channel + "_TEST", operatorId, channelTradeNo);
        return payments(patientId, businessId).stream().findFirst().orElseThrow();
    }

    public List<Payment> payments(String patientId, String businessId) {
        return jdbc.query("""
                select * from payment_order
                where (? is null or patient_id = ?) and (? is null or business_id = ?)
                order by created_at desc
                """, (rs, rowNum) -> new Payment(
                rs.getString("id"), rs.getString("business_type"), rs.getString("business_id"),
                rs.getString("patient_id"), rs.getBigDecimal("amount"), rs.getString("payment_method"),
                rs.getString("status"), rs.getString("operator_id"), time(rs.getTimestamp("paid_at"))),
                patientId, patientId, businessId, businessId);
    }

    public List<Refund> refunds(String patientId, String businessId) {
        return jdbc.query("""
                select * from refund_order
                where (? is null or patient_id = ?) and (? is null or business_id = ?)
                order by created_at desc
                """, (rs, rowNum) -> new Refund(
                rs.getString("id"), rs.getString("business_type"), rs.getString("business_id"),
                rs.getString("patient_id"), rs.getBigDecimal("amount"), rs.getString("reason"),
                rs.getString("status"), rs.getString("operator_id"), time(rs.getTimestamp("refunded_at"))),
                patientId, patientId, businessId, businessId);
    }

    private static LocalDateTime time(java.sql.Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    public record Payment(
            String id, String businessType, String businessId, String patientId, BigDecimal amount,
            String paymentMethod, String status, String operatorId, LocalDateTime paidAt) {}
    public record Refund(
            String id, String businessType, String businessId, String patientId, BigDecimal amount,
            String reason, String status, String operatorId, LocalDateTime refundedAt) {}
}
