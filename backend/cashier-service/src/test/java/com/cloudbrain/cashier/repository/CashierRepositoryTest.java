package com.cloudbrain.cashier.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class CashierRepositoryTest {
    @Mock
    JdbcTemplate jdbcTemplate;

    @Test
    void recordRefundLoadsPaidAmountWhenRefundAmountIsNull() {
        CashierRepository repository = spy(new CashierRepository(jdbcTemplate));
        CashierRepository.Payment payment = payment("payment-1", "PRESCRIPTION", "biz-1", "patient-1", "PAID");
        when(jdbcTemplate.query(
                eq("select amount from payment where business_type=? and business_id=? and status='PAID'"),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<BigDecimal>>any(),
                eq("PRESCRIPTION"),
                eq("biz-1")))
                .thenReturn(List.of(new BigDecimal("18.88")));
        doReturn(payment).when(repository).findByBusiness("PRESCRIPTION", "biz-1");

        assertThatThrownBy(() -> repository.recordRefund("PRESCRIPTION", "biz-1", "patient-1", null, "reason", "operator-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("退款记录生成失败");
        verify(jdbcTemplate).update(anyString(), any(), eq("payment-1"), eq(new BigDecimal("18.88")), eq("reason"), eq("operator-1"));
    }

    @Test
    void recordRefundReturnsExistingRefundWhenPaymentAlreadyRefunded() {
        CashierRepository repository = spy(new CashierRepository(jdbcTemplate));
        CashierRepository.Payment payment = payment("payment-1", "PRESCRIPTION", "biz-1", "patient-1", "REFUNDED");
        CashierRepository.Refund refund = refund("refund-1", "PRESCRIPTION", "biz-1", "patient-1");
        doReturn(payment).when(repository).findByBusiness("PRESCRIPTION", "biz-1");
        doReturn(List.of(refund)).when(repository).refunds("patient-1", "biz-1");

        CashierRepository.Refund result = repository.recordRefund("PRESCRIPTION", "biz-1", "patient-1", BigDecimal.ONE, "reason", "operator-1");

        assertThat(result).isSameAs(refund);
        verify(jdbcTemplate, never()).update(
                "update payment set status = 'REFUNDED' where id = ?::uuid and status = 'PAID'",
                "payment-1");
    }

    @Test
    void recordTestPaymentByIdRejectsNonScannablePaymentStatus() {
        CashierRepository repository = spy(new CashierRepository(jdbcTemplate));
        doReturn(payment("payment-1", "APPOINTMENT", "biz-1", "patient-1", "FAILED")).when(repository).findById("payment-1");

        assertThatThrownBy(() -> repository.recordTestPaymentById("payment-1", "WECHAT", "operator-1", "trade-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("payment is not scannable in the current status");
    }

    @Test
    void recordTestFailureCreatesPendingOrderThenReturnsLatestPayment() {
        CashierRepository repository = spy(new CashierRepository(jdbcTemplate));
        CashierRepository.Payment failed = payment("payment-2", "MEDICAL_ORDER", "biz-2", "patient-2", "FAILED");
        doReturn(failed).when(repository).findByBusiness("MEDICAL_ORDER", "biz-2");

        CashierRepository.Payment result = repository.recordTestFailure("MEDICAL_ORDER", "biz-2", "patient-2", "operator-2", "SIMULATED_FAILURE");

        assertThat(result).isSameAs(failed);
        verify(jdbcTemplate).update(
                "update payment set status='FAILED',failure_reason=? where business_type=? and business_id=? and status='PENDING'",
                "SIMULATED_FAILURE",
                "MEDICAL_ORDER",
                "biz-2");
    }

    private CashierRepository.Payment payment(String id, String businessType, String businessId, String patientId, String status) {
        return new CashierRepository.Payment(
                id,
                businessType,
                businessId,
                patientId,
                new BigDecimal("10.00"),
                "WECHAT_TEST",
                status,
                "operator-1",
                LocalDateTime.of(2026, 7, 9, 10, 0),
                LocalDateTime.of(2026, 7, 9, 9, 0),
                null,
                "trade-1",
                null,
                null,
                null,
                null);
    }

    private CashierRepository.Refund refund(String id, String businessType, String businessId, String patientId) {
        return new CashierRepository.Refund(
                id,
                businessType,
                businessId,
                patientId,
                new BigDecimal("18.88"),
                "reason",
                "REFUNDED",
                "operator-1",
                LocalDateTime.of(2026, 7, 9, 11, 0));
    }
}
