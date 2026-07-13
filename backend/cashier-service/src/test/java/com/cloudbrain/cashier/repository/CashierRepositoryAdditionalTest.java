package com.cloudbrain.cashier.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class CashierRepositoryAdditionalTest {
    @Mock
    JdbcTemplate jdbcTemplate;

    @Test
    void findByBusinessMapsPaymentRow() throws Exception {
        CashierRepository repository = new CashierRepository(jdbcTemplate);
        when(jdbcTemplate.query(
                eq("select * from payment where business_type=? and business_id=?"),
                any(org.springframework.jdbc.core.RowMapper.class),
                eq("PRESCRIPTION"),
                eq("biz-1")))
                .thenAnswer(invocation -> mapPayment(invocation, paymentResultSet(
                        Map.ofEntries(
                                Map.entry("id", "payment-1"),
                                Map.entry("business_type", "PRESCRIPTION"),
                                Map.entry("business_id", "biz-1"),
                                Map.entry("patient_id", "patient-1"),
                                Map.entry("amount", new BigDecimal("18.88")),
                                Map.entry("method", "WECHAT"),
                                Map.entry("status", "PAID"),
                                Map.entry("operator_id", "cashier-1"),
                                Map.entry("paid_at", Timestamp.valueOf(LocalDateTime.of(2026, 7, 9, 10, 0))),
                                Map.entry("created_at", Timestamp.valueOf(LocalDateTime.of(2026, 7, 9, 9, 0))),
                                Map.entry("channel_trade_no", "trade-1")))));

        CashierRepository.Payment result = repository.findByBusiness("PRESCRIPTION", "biz-1");

        assertThat(result.amount()).isEqualByComparingTo("18.88");
        assertThat(result.paidAt()).isEqualTo(LocalDateTime.of(2026, 7, 9, 10, 0));
        assertThat(result.channelTradeNo()).isEqualTo("trade-1");
    }

    @Test
    void findOptionalByIdMapsPaymentWithNullPaidAt() throws Exception {
        CashierRepository repository = new CashierRepository(jdbcTemplate);
        when(jdbcTemplate.query(
                eq("select * from payment where id=?::uuid"),
                any(org.springframework.jdbc.core.RowMapper.class),
                eq("payment-1")))
                .thenAnswer(invocation -> mapPayment(invocation, paymentResultSet(
                        entries(
                                new java.util.AbstractMap.SimpleEntry<>("id", "payment-1"),
                                new java.util.AbstractMap.SimpleEntry<>("business_type", "APPOINTMENT"),
                                new java.util.AbstractMap.SimpleEntry<>("business_id", "biz-1"),
                                new java.util.AbstractMap.SimpleEntry<>("patient_id", "patient-1"),
                                new java.util.AbstractMap.SimpleEntry<>("amount", new BigDecimal("0.01")),
                                new java.util.AbstractMap.SimpleEntry<>("method", "WECHAT_TEST"),
                                new java.util.AbstractMap.SimpleEntry<>("status", "PENDING"),
                                new java.util.AbstractMap.SimpleEntry<>("operator_id", "cashier-1"),
                                new java.util.AbstractMap.SimpleEntry<>("paid_at", null),
                                new java.util.AbstractMap.SimpleEntry<>("created_at", Timestamp.valueOf(LocalDateTime.of(2026, 7, 9, 9, 0))),
                                new java.util.AbstractMap.SimpleEntry<>("channel_trade_no", null)))));

        Optional<CashierRepository.Payment> result = repository.findOptionalById("payment-1");

        assertThat(result).isPresent();
        assertThat(result.get().paidAt()).isNull();
    }

    @Test
    void refundsMapJoinedRefundRows() throws Exception {
        CashierRepository repository = new CashierRepository(jdbcTemplate);
        when(jdbcTemplate.query(
                argThat(sql -> sql.contains("from refund r") && sql.contains("join payment p")),
                any(org.springframework.jdbc.core.RowMapper.class),
                eq("patient-1"),
                eq("biz-1")))
                .thenAnswer(invocation -> mapRefund(invocation, refundResultSet(
                        Map.of(
                                "id", "refund-1",
                                "business_type", "PRESCRIPTION",
                                "business_id", "biz-1",
                                "patient_id", "patient-1",
                                "amount", new BigDecimal("18.88"),
                                "reason", "reason",
                                "status", "REFUNDED",
                                "operator_id", "cashier-1",
                                "refunded_at", Timestamp.valueOf(LocalDateTime.of(2026, 7, 9, 11, 0))))));

        List<CashierRepository.Refund> result = repository.refunds("patient-1", "biz-1");

        assertThat(result).singleElement().satisfies(refund -> {
            assertThat(refund.businessType()).isEqualTo("PRESCRIPTION");
            assertThat(refund.refundedAt()).isEqualTo(LocalDateTime.of(2026, 7, 9, 11, 0));
        });
    }

    @Test
    void findByIdThrowsWhenPaymentIsMissing() {
        CashierRepository repository = new CashierRepository(jdbcTemplate);
        when(jdbcTemplate.query(
                eq("select * from payment where id=?::uuid"),
                any(org.springframework.jdbc.core.RowMapper.class),
                eq("missing"))).thenReturn(List.of());

        assertThatThrownBy(() -> repository.findById("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payment not found");
    }

    @Test
    void recordPaymentInsertsPaidPaymentRow() {
        CashierRepository repository = new CashierRepository(jdbcTemplate);

        repository.recordPayment("APPOINTMENT", "biz-1", "patient-1", BigDecimal.TEN, "WECHAT", "cashier-1");

        verify(jdbcTemplate).update(
                argThat(sql -> sql.contains("insert into payment") && sql.contains("'PAID'")),
                anyString(),
                eq("APPOINTMENT"),
                eq("biz-1"),
                eq("patient-1"),
                eq(BigDecimal.TEN),
                eq("WECHAT"),
                eq("cashier-1"));
    }

    @Test
    void createPaymentOrderReturnsLatestBusinessPayment() {
        CashierRepository repository = spy(new CashierRepository(jdbcTemplate));
        CashierRepository.Payment payment = payment("payment-1", "APPOINTMENT", "biz-1", "patient-1", "PENDING");
        doReturn(payment).when(repository).findByBusiness("APPOINTMENT", "biz-1");

        CashierRepository.Payment result = repository.createPaymentOrder(
                "APPOINTMENT",
                "biz-1",
                "patient-1",
                new BigDecimal("0.01"),
                "WECHAT",
                "cashier-1");

        assertThat(result).isSameAs(payment);
        verify(jdbcTemplate).update(
                argThat(sql -> sql.contains("insert into payment") && sql.contains("where payment.status = 'PENDING'")),
                anyString(),
                eq("APPOINTMENT"),
                eq("biz-1"),
                eq("patient-1"),
                eq(new BigDecimal("0.01")),
                eq("WECHAT"),
                eq("cashier-1"));
    }

    @Test
    void recordTestPaymentRejectsMismatchedPatient() {
        CashierRepository repository = spy(new CashierRepository(jdbcTemplate));
        doReturn(payment("payment-1", "MEDICAL_ORDER", "biz-1", "patient-2", "PENDING"))
                .when(repository).findByBusiness("MEDICAL_ORDER", "biz-1");

        assertThatThrownBy(() -> repository.recordTestPayment(
                "MEDICAL_ORDER",
                "biz-1",
                "patient-1",
                "SIMULATED",
                "cashier-1",
                "trade-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordTestPaymentReturnsUpdatedPaymentForMatchingPatient() {
        CashierRepository repository = spy(new CashierRepository(jdbcTemplate));
        CashierRepository.Payment pending = payment("payment-1", "MEDICAL_ORDER", "biz-1", "patient-1", "PENDING");
        CashierRepository.Payment paid = payment("payment-1", "MEDICAL_ORDER", "biz-1", "patient-1", "PAID");
        doReturn(pending).doReturn(paid).when(repository).findByBusiness("MEDICAL_ORDER", "biz-1");

        CashierRepository.Payment result = repository.recordTestPayment(
                "MEDICAL_ORDER",
                "biz-1",
                "patient-1",
                "SIMULATED",
                "cashier-1",
                "trade-1");

        assertThat(result).isSameAs(paid);
        verify(jdbcTemplate).update(
                argThat(sql -> sql.contains("update payment set status='PAID'")),
                eq("SIMULATED_TEST"),
                eq("cashier-1"),
                eq("trade-1"),
                eq("payment-1"));
    }

    @Test
    void recordTestPaymentByIdAllowsAlreadyPaidOrders() {
        CashierRepository repository = spy(new CashierRepository(jdbcTemplate));
        CashierRepository.Payment alreadyPaid = payment("payment-1", "APPOINTMENT", "biz-1", "patient-1", "PAID");
        doReturn(alreadyPaid).doReturn(alreadyPaid).when(repository).findById("payment-1");

        CashierRepository.Payment result = repository.recordTestPaymentById("payment-1", "WECHAT", "PUBLIC_SCAN", "trade-1");

        assertThat(result).isSameAs(alreadyPaid);
        verify(jdbcTemplate).update(
                argThat(sql -> sql.contains("update payment set status='PAID'")),
                eq("WECHAT_TEST"),
                eq("PUBLIC_SCAN"),
                eq("trade-1"),
                eq("payment-1"));
    }

    @Test
    void paymentsBuildsSqlFromNonBlankFilters() {
        CashierRepository repository = new CashierRepository(jdbcTemplate);
        List<CashierRepository.Payment> payments = List.of(payment("payment-1", "PRESCRIPTION", "biz-1", "patient-1", "PAID"));
        when(jdbcTemplate.query(
                argThat(sql -> sql.contains("patient_id = ?::uuid")
                        && sql.contains("business_id = ?")
                        && sql.contains("business_type = ?")
                        && sql.contains("status = ?")),
                any(org.springframework.jdbc.core.RowMapper.class),
                eq("patient-1"),
                eq("biz-1"),
                eq("PRESCRIPTION"),
                eq("PAID"))).thenReturn(payments);

        List<CashierRepository.Payment> result = repository.payments("patient-1", "biz-1", "PRESCRIPTION", "PAID");

        assertThat(result).isSameAs(payments);
    }

    @Test
    void refundsBuildsSqlFromNonBlankFilters() {
        CashierRepository repository = new CashierRepository(jdbcTemplate);
        List<CashierRepository.Refund> refunds = List.of(refund("refund-1", "PRESCRIPTION", "biz-1", "patient-1"));
        when(jdbcTemplate.query(
                argThat(sql -> sql.contains("p.patient_id = ?::uuid") && sql.contains("p.business_id = ?")),
                any(org.springframework.jdbc.core.RowMapper.class),
                eq("patient-1"),
                eq("biz-1"))).thenReturn(refunds);

        List<CashierRepository.Refund> result = repository.refunds("patient-1", "biz-1");

        assertThat(result).isSameAs(refunds);
    }

    @Test
    void recordRefundLoadsPaidAmountWhenRefundAmountIsZero() {
        CashierRepository repository = spy(new CashierRepository(jdbcTemplate));
        CashierRepository.Payment payment = payment("payment-7", "PRESCRIPTION", "biz-7", "patient-7", "PAID");
        when(jdbcTemplate.query(
                eq("select amount from payment where business_type=? and business_id=? and status='PAID'"),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<BigDecimal>>any(),
                eq("PRESCRIPTION"),
                eq("biz-7")))
                .thenReturn(List.of(new BigDecimal("18.88")));
        doReturn(payment).when(repository).findByBusiness("PRESCRIPTION", "biz-7");

        assertThatThrownBy(() -> repository.recordRefund(
                "PRESCRIPTION",
                "biz-7",
                "patient-7",
                BigDecimal.ZERO,
                "reason",
                "operator-7"))
                .isInstanceOf(IllegalStateException.class);

        verify(jdbcTemplate).update(anyString(), any(), eq("payment-7"), eq(new BigDecimal("18.88")), eq("reason"), eq("operator-7"));
    }

    @Test
    void findOptionalByBusinessReturnsEmptyWhenNoPaymentExists() {
        CashierRepository repository = new CashierRepository(jdbcTemplate);
        when(jdbcTemplate.query(
                eq("select * from payment where business_type=? and business_id=?"),
                any(org.springframework.jdbc.core.RowMapper.class),
                eq("PRESCRIPTION"),
                eq("biz-1"))).thenReturn(List.of());

        Optional<CashierRepository.Payment> result = repository.findOptionalByBusiness("PRESCRIPTION", "biz-1");

        assertThat(result).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private List<CashierRepository.Payment> mapPayment(
            org.mockito.invocation.InvocationOnMock invocation,
            java.sql.ResultSet resultSet) throws Exception {
        var rowMapper = (org.springframework.jdbc.core.RowMapper<CashierRepository.Payment>) invocation.getArgument(1);
        return List.of(rowMapper.mapRow(resultSet, 0));
    }

    @SuppressWarnings("unchecked")
    private List<CashierRepository.Refund> mapRefund(
            org.mockito.invocation.InvocationOnMock invocation,
            java.sql.ResultSet resultSet) throws Exception {
        var rowMapper = (org.springframework.jdbc.core.RowMapper<CashierRepository.Refund>) invocation.getArgument(1);
        return List.of(rowMapper.mapRow(resultSet, 0));
    }

    private java.sql.ResultSet paymentResultSet(Map<String, Object> values) throws Exception {
        java.sql.ResultSet resultSet = org.mockito.Mockito.mock(java.sql.ResultSet.class);
        when(resultSet.getString(anyString())).thenAnswer(invocation -> {
            Object value = values.get(invocation.getArgument(0, String.class));
            return value == null ? null : value.toString();
        });
        when(resultSet.getBigDecimal(anyString())).thenAnswer(invocation ->
                (BigDecimal) values.get(invocation.getArgument(0, String.class)));
        when(resultSet.getTimestamp(anyString())).thenAnswer(invocation ->
                (Timestamp) values.get(invocation.getArgument(0, String.class)));
        return resultSet;
    }

    private java.sql.ResultSet refundResultSet(Map<String, Object> values) throws Exception {
        return paymentResultSet(values);
    }

    @SafeVarargs
    private final Map<String, Object> entries(Map.Entry<String, Object>... entries) {
        java.util.LinkedHashMap<String, Object> values = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : entries) {
            values.put(entry.getKey(), entry.getValue());
        }
        return values;
    }

    private CashierRepository.Payment payment(
            String id,
            String businessType,
            String businessId,
            String patientId,
            String status) {
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
                "trade-1");
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
