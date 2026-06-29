package com.cloudbrain.cashier.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
                insert into payment
                    (id, business_type, business_id, patient_id, amount, method, status, operator_id, paid_at)
                values (?::uuid, ?, ?, ?::uuid, ?, ?, 'PAID', ?, now())
                on conflict (business_type, business_id) do nothing
                """, UUID.randomUUID().toString(), type, businessId, patientId, amount, method, operatorId);
    }

    public Payment createPaymentOrder(String type,String businessId,String patientId,BigDecimal amount,
            String method,String operatorId) {
        jdbc.update("""
                insert into payment
                    (id,business_type,business_id,patient_id,amount,method,status,operator_id)
                values (?::uuid,?,?,?::uuid,?,?,'PENDING',?)
                on conflict (business_type,business_id) do nothing
                """,UUID.randomUUID().toString(),type,businessId,patientId,amount,method,operatorId);
        return findByBusiness(type,businessId);
    }

    public void recordRefund(
            String type, String businessId, String patientId,
            BigDecimal amount, String reason, String operatorId) {
        BigDecimal refundAmount=amount;
        if(refundAmount==null || refundAmount.signum()==0) {
            refundAmount=jdbc.query("select amount from payment where business_type=? and business_id=? and status='PAID'",
                    (rs,row)->rs.getBigDecimal(1),type,businessId).stream().findFirst().orElse(BigDecimal.ZERO);
        }
        Payment payment = findByBusiness(type, businessId);
        jdbc.update("""
                insert into refund
                    (id, payment_id, amount, reason, status, operator_id, refunded_at)
                values (?::uuid, ?::uuid, ?, ?, 'REFUNDED', ?, now())
                """, UUID.randomUUID().toString(), payment.id(), refundAmount, reason, operatorId);
    }

    @Transactional
    public Payment recordTestPayment(String businessType, String businessId, String patientId,
                                     String channel, String operatorId, String channelTradeNo) {
        jdbc.update("""
                insert into payment
                    (id, business_type, business_id, patient_id, amount, method, status, operator_id)
                values (?::uuid, ?, ?, ?::uuid, 0.01, ?, 'PENDING', ?)
                on conflict (business_type, business_id) do nothing
                """, UUID.randomUUID().toString(), businessType, businessId, patientId,
                channel + "_TEST", operatorId);
        Payment existing=findByBusiness(businessType,businessId);
        if(!existing.patientId().equals(patientId)) throw new IllegalArgumentException("支付单患者不匹配");
        jdbc.update("""
                update payment set status='PAID', method=?, operator_id=?,
                    paid_at=coalesce(paid_at,now()),
                    channel_trade_no=coalesce(channel_trade_no,?)
                where id=?::uuid and status='PENDING'
                """,channel+"_TEST",operatorId,channelTradeNo,existing.id());
        return findByBusiness(businessType,businessId);
    }

    public Payment findByBusiness(String type,String businessId) {
        return jdbc.query("select * from payment where business_type=? and business_id=?",
                (rs,row)->payment(rs),type,businessId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("支付单不存在"));
    }

    @Transactional
    public Payment recordTestFailure(String type,String businessId,String patientId,String operatorId,String reason) {
        createPaymentOrder(type,businessId,patientId,new BigDecimal("0.01"),"WECHAT_TEST",operatorId);
        jdbc.update("update payment set status='FAILED',failure_reason=? where business_type=? and business_id=? and status='PENDING'",
                reason,type,businessId);
        return findByBusiness(type,businessId);
    }

    public List<Payment> payments(String patientId, String businessId, String businessType, String status) {
        StringBuilder sql = new StringBuilder("""
                select * from payment
                where 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (patientId != null && !patientId.isBlank()) {
            sql.append(" and patient_id = ?::uuid");
            args.add(patientId);
        }
        if (businessId != null && !businessId.isBlank()) {
            sql.append(" and business_id = ?");
            args.add(businessId);
        }
        if (businessType != null && !businessType.isBlank()) {
            sql.append(" and business_type = ?");
            args.add(businessType);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" and status = ?");
            args.add(status);
        }
        sql.append(" order by created_at desc");
        return jdbc.query(sql.toString(), (rs, rowNum) -> payment(rs), args.toArray());
    }

    private Payment payment(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Payment(rs.getString("id"),rs.getString("business_type"),rs.getString("business_id"),
                rs.getString("patient_id"),rs.getBigDecimal("amount"),rs.getString("method"),
                rs.getString("status"),rs.getString("operator_id"),time(rs.getTimestamp("paid_at")),
                time(rs.getTimestamp("created_at")),null,rs.getString("channel_trade_no"));
    }

    public List<Refund> refunds(String patientId, String businessId) {
        StringBuilder sql = new StringBuilder("""
                select r.*, p.business_type, p.business_id, p.patient_id
                from refund r
                join payment p on p.id = r.payment_id
                where 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (patientId != null && !patientId.isBlank()) {
            sql.append(" and p.patient_id = ?::uuid");
            args.add(patientId);
        }
        if (businessId != null && !businessId.isBlank()) {
            sql.append(" and p.business_id = ?");
            args.add(businessId);
        }
        sql.append(" order by created_at desc");
        return jdbc.query(sql.toString(), (rs, rowNum) -> new Refund(
                rs.getString("id"), rs.getString("business_type"), rs.getString("business_id"),
                rs.getString("patient_id"), rs.getBigDecimal("amount"), rs.getString("reason"),
                rs.getString("status"), rs.getString("operator_id"), time(rs.getTimestamp("refunded_at"))),
                args.toArray());
    }

    private static LocalDateTime time(java.sql.Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    public record Payment(
            String id, String businessType, String businessId, String patientId, BigDecimal amount,
            String paymentMethod, String status, String operatorId, LocalDateTime paidAt,
            LocalDateTime createdAt,String paymentScene,String channelTradeNo) {}
    public record Refund(
            String id, String businessType, String businessId, String patientId, BigDecimal amount,
            String reason, String status, String operatorId, LocalDateTime refundedAt) {}
}
