package com.cloudbrain.cashier.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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

    public Payment createPaymentOrder(String type,String businessId,String patientId,BigDecimal amount,
            String method,String operatorId) {
        jdbc.update("""
                insert into payment_order
                    (id,business_type,business_id,patient_id,amount,payment_method,status,operator_id,payment_scene)
                values (?,?,?,?,?,?,'PENDING',?,'BUSINESS')
                on conflict (business_type,business_id) do nothing
                ""","pay-"+UUID.randomUUID(),type,businessId,patientId,amount,method,operatorId);
        return findByBusiness(type,businessId);
    }

    public void recordRefund(
            String type, String businessId, String patientId,
            BigDecimal amount, String reason, String operatorId) {
        BigDecimal refundAmount=amount;
        if(refundAmount==null || refundAmount.signum()==0) {
            refundAmount=jdbc.query("select amount from payment_order where business_type=? and business_id=? and status='PAID'",
                    (rs,row)->rs.getBigDecimal(1),type,businessId).stream().findFirst().orElse(BigDecimal.ZERO);
        }
        jdbc.update("""
                insert into refund_order
                    (id, business_type, business_id, patient_id, amount, reason, status, operator_id, refunded_at)
                values (?, ?, ?, ?, ?, ?, 'REFUNDED', ?, now())
                on conflict (business_type, business_id) do nothing
                """, "refund-" + UUID.randomUUID(), type, businessId, patientId, refundAmount, reason, operatorId);
    }

    @Transactional
    public Payment recordTestPayment(String businessType, String businessId, String patientId,
                                     String channel, String operatorId, String channelTradeNo) {
        jdbc.update("""
                insert into payment_order
                    (id, business_type, business_id, patient_id, amount, payment_method, status,
                     operator_id, payment_scene)
                values (?, ?, ?, ?, 0.01, ?, 'PENDING', ?, 'SANDBOX_0_01')
                on conflict (business_type, business_id) do nothing
                """, "pay-test-" + UUID.randomUUID(), businessType, businessId, patientId,
                channel + "_TEST", operatorId);
        Payment existing=findByBusiness(businessType,businessId);
        if(!existing.patientId().equals(patientId)) throw new IllegalArgumentException("支付单患者不匹配");
        jdbc.update("""
                update payment_order set status='PAID', payment_method=?, operator_id=?,
                    paid_at=coalesce(paid_at,now()), payment_scene='SANDBOX_0_01',
                    channel_trade_no=coalesce(channel_trade_no,?), callback_received_at=now()
                where id=? and status='PENDING'
                """,channel+"_TEST",operatorId,channelTradeNo,existing.id());
        return findByBusiness(businessType,businessId);
    }

    public Payment findByBusiness(String type,String businessId) {
        return jdbc.query("select * from payment_order where business_type=? and business_id=?",
                (rs,row)->payment(rs),type,businessId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("支付单不存在"));
    }

    @Transactional
    public Payment recordTestFailure(String type,String businessId,String patientId,String operatorId,String reason) {
        createPaymentOrder(type,businessId,patientId,new BigDecimal("0.01"),"WECHAT_TEST",operatorId);
        jdbc.update("update payment_order set status='FAILED',failure_reason=?,callback_received_at=now() where business_type=? and business_id=? and status='PENDING'",
                reason,type,businessId);
        return findByBusiness(type,businessId);
    }

    public List<Payment> payments(String patientId, String businessId) {
        return jdbc.query("""
                select * from payment_order
                where (? is null or patient_id = ?) and (? is null or business_id = ?)
                order by created_at desc
                """, (rs, rowNum) -> payment(rs),
                patientId, patientId, businessId, businessId);
    }

    private Payment payment(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Payment(rs.getString("id"),rs.getString("business_type"),rs.getString("business_id"),
                rs.getString("patient_id"),rs.getBigDecimal("amount"),rs.getString("payment_method"),
                rs.getString("status"),rs.getString("operator_id"),time(rs.getTimestamp("paid_at")),
                rs.getString("payment_scene"),rs.getString("channel_trade_no"));
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
            String paymentMethod, String status, String operatorId, LocalDateTime paidAt,
            String paymentScene,String channelTradeNo) {}
    public record Refund(
            String id, String businessType, String businessId, String patientId, BigDecimal amount,
            String reason, String status, String operatorId, LocalDateTime refundedAt) {}
}
