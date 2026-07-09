package com.cloudbrain.cashier.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.cashier.audit.AuditPublisher;
import com.cloudbrain.cashier.repository.CashierRepository;
import com.cloudbrain.cashier.service.AppointmentPaymentClient;
import com.cloudbrain.cashier.service.MedicalOrderPaymentClient;
import com.cloudbrain.cashier.service.PatientAccessClient;
import com.cloudbrain.cashier.service.PrescriptionPaymentClient;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;

@ExtendWith(MockitoExtension.class)
class CashierControllerTest {
    @Mock
    CashierRepository repository;

    @Mock
    AppointmentPaymentClient appointmentClient;

    @Mock
    MedicalOrderPaymentClient medicalOrderClient;

    @Mock
    PrescriptionPaymentClient prescriptionClient;

    @Mock
    PatientAccessClient patientAccessClient;

    @Mock
    AuditPublisher auditPublisher;

    @Mock
    HttpServletRequest request;

    @Test
    void createOrderForPatientFallsBackToBoundPatientAndDefaultsAmountAndMethod() {
        CashierController controller = controller(true, "");
        CashierRepository.Payment payment = payment("payment-1", "APPOINTMENT", "biz-1", "patient-1", "PENDING", "WECHAT");
        when(patientAccessClient.boundPatientId("account-1")).thenReturn("patient-1");
        when(patientAccessClient.owns("account-1", "patient-1")).thenReturn(true);
        when(repository.createPaymentOrder(
                eq("APPOINTMENT"),
                eq("biz-1"),
                eq("patient-1"),
                eq(new BigDecimal("0.01")),
                eq("WECHAT"),
                eq("account-1")))
                .thenReturn(payment);

        CashierRepository.Payment result = controller.createOrder(
                new CashierController.CreatePaymentRequest("APPOINTMENT", "biz-1", null, null, null),
                authentication("account-1", "PATIENT"));

        assertThat(result).isSameAs(payment);
        verify(repository).createPaymentOrder("APPOINTMENT", "biz-1", "patient-1", new BigDecimal("0.01"), "WECHAT", "account-1");
        verify(auditPublisher).publish(
                "PAYMENT_ORDER_CREATE",
                "PAYMENT_ORDER",
                "payment-1",
                "patient-1",
                "biz-1",
                "account-1",
                "PATIENT",
                java.util.Map.of(
                        "businessType", "APPOINTMENT",
                        "amount", new BigDecimal("0.01"),
                        "status", "PENDING"));
    }

    @Test
    void createOrderRejectsPatientWhenOwnershipCheckFails() {
        CashierController controller = controller(true, "");
        when(patientAccessClient.owns("account-1", "patient-2")).thenReturn(false);

        assertThatThrownBy(() -> controller.createOrder(
                new CashierController.CreatePaymentRequest("APPOINTMENT", "biz-1", "patient-2", BigDecimal.ONE, "ALIPAY"),
                authentication("account-1", "PATIENT")))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).createPaymentOrder(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void paymentsUsesBoundPatientForPatientRole() {
        CashierController controller = controller(true, "");
        List<CashierRepository.Payment> payments = List.of(payment("payment-1", "MEDICAL_ORDER", "biz-1", "patient-1", "PAID", "ALIPAY_TEST"));
        when(patientAccessClient.boundPatientId("account-1")).thenReturn("patient-1");
        when(patientAccessClient.owns("account-1", "patient-1")).thenReturn(true);
        when(repository.payments("patient-1", null, null, null)).thenReturn(payments);

        List<CashierRepository.Payment> result = controller.payments(null, null, null, null, authentication("account-1", "PATIENT"));

        assertThat(result).isSameAs(payments);
        verify(repository).payments("patient-1", null, null, null);
    }

    @Test
    void testFailureNotifiesAppointmentClientForAppointmentPayments() {
        CashierController controller = controller(true, "");
        CashierRepository.Payment payment = payment("payment-1", "APPOINTMENT", "appt-1", "patient-1", "FAILED", "WECHAT_TEST");
        when(patientAccessClient.owns("account-1", "patient-1")).thenReturn(true);
        when(repository.recordTestFailure("APPOINTMENT", "appt-1", "patient-1", "account-1", "SIMULATED_FAILURE"))
                .thenReturn(payment);

        CashierRepository.Payment result = controller.testFailure(
                new CashierController.TestPaymentRequest("APPOINTMENT", "appt-1", "patient-1", null, null),
                authentication("account-1", "PATIENT"));

        assertThat(result).isSameAs(payment);
        verify(appointmentClient).fail("appt-1", "patient-1", "payment-1");
        verify(auditPublisher).publish(
                "PAYMENT_FAILED",
                "PAYMENT_ORDER",
                "payment-1",
                "patient-1",
                "appt-1",
                "account-1",
                "PATIENT",
                java.util.Map.of(
                        "businessType", "APPOINTMENT",
                        "status", "FAILED"));
    }

    @Test
    void testCallbackConfirmsMedicalOrderPayment() {
        CashierController controller = controller(true, "");
        CashierRepository.Payment payment = payment("payment-2", "MEDICAL_ORDER", "order-1", "patient-1", "PAID", "SIMULATED_TEST");
        when(patientAccessClient.owns("account-1", "patient-1")).thenReturn(true);
        when(repository.recordTestPayment(
                "MEDICAL_ORDER",
                "order-1",
                "patient-1",
                "SIMULATED",
                "account-1",
                "trade-no-1"))
                .thenReturn(payment);

        CashierRepository.Payment result = controller.testCallback(
                new CashierController.TestPaymentRequest("MEDICAL_ORDER", "order-1", "patient-1", "SIMULATED", "trade-no-1"),
                authentication("account-1", "PATIENT"));

        assertThat(result).isSameAs(payment);
        verify(medicalOrderClient).confirm("order-1", "patient-1", "payment-2");
        verify(auditPublisher).publish(
                eq("PAYMENT_CONFIRMED"),
                eq("PAYMENT_ORDER"),
                eq("payment-2"),
                eq("patient-1"),
                eq("order-1"),
                eq("account-1"),
                eq("PATIENT"),
                any());
    }

    @Test
    void refundDrugReturnRecordsRefundAndCompletesReturn() {
        CashierController controller = controller(true, "");
        CashierRepository.Payment payment = payment("payment-3", "PRESCRIPTION", "pres-1", "patient-1", "PAID", "WECHAT_TEST");
        CashierRepository.Refund refund = refund("refund-1", "PRESCRIPTION", "pres-1", "patient-1");
        when(patientAccessClient.owns("account-1", "patient-1")).thenReturn(true);
        when(repository.findOptionalByBusiness("PRESCRIPTION", "pres-1")).thenReturn(java.util.Optional.of(payment));
        when(repository.recordRefund("PRESCRIPTION", "pres-1", "patient-1", BigDecimal.TEN, "custom reason", "account-1"))
                .thenReturn(refund);

        Object result = controller.refundDrugReturn(
                new CashierController.DrugReturnRefundRequest("return-1", "pres-1", "patient-1", BigDecimal.TEN, "custom reason"),
                authentication("account-1", "PATIENT"));

        assertThat(result).isSameAs(refund);
        verify(prescriptionClient).completeDrugReturn("return-1", "account-1", "refund-1");
        verify(auditPublisher).publish(
                eq("PAYMENT_REFUND"),
                eq("REFUND"),
                eq("refund-1"),
                eq("patient-1"),
                eq("pres-1"),
                eq("account-1"),
                eq("PATIENT"),
                any());
    }

    @Test
    void scanEntryForNewAppointmentPaymentConfirmsBusinessPayment() {
        CashierController controller = controller(true, "");
        CashierRepository.Payment before = payment("payment-4", "APPOINTMENT", "appt-2", "patient-1", "PENDING", "WECHAT_TEST");
        CashierRepository.Payment after = payment("payment-4", "APPOINTMENT", "appt-2", "patient-1", "PAID", "WECHAT_TEST");
        when(repository.findById("payment-4")).thenReturn(before);
        when(repository.recordTestPaymentById(eq("payment-4"), eq("WECHAT"), eq("PUBLIC_SCAN"), any()))
                .thenReturn(after);

        var response = controller.scanEntry("payment-4", "WECHAT");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_HTML);
        assertThat(response.getBody()).contains("<html");
        verify(appointmentClient).confirm("appt-2", "patient-1", "WECHAT_TEST", "payment-4");
        verify(auditPublisher).publish(
                eq("PAYMENT_CONFIRMED"),
                eq("PAYMENT_ORDER"),
                eq("payment-4"),
                eq("patient-1"),
                eq("appt-2"),
                eq("PUBLIC_SCAN"),
                eq("SYSTEM"),
                any());
    }

    @Test
    void refundScanEntryForPrescriptionCompletesDrugReturnRefund() {
        CashierController controller = controller(true, "https://cashier.example");
        CashierRepository.Payment payment = payment("payment-5", "PRESCRIPTION", "pres-2", "patient-1", "PAID", "WECHAT_TEST");
        CashierRepository.Refund refund = refund("refund-2", "PRESCRIPTION", "pres-2", "patient-1");
        when(repository.findOptionalByBusiness("PRESCRIPTION", "pres-2")).thenReturn(java.util.Optional.of(payment));
        when(repository.recordRefund(
                "PRESCRIPTION",
                "pres-2",
                "patient-1",
                new BigDecimal("12.34"),
                "Drug return refund via scan",
                "PUBLIC_SCAN"))
                .thenReturn(refund);

        var response = controller.refundScanEntry(
                "PRESCRIPTION",
                "pres-2",
                "patient-1",
                "return-2",
                new BigDecimal("12.34"),
                "ALIPAY");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("<html");
        verify(prescriptionClient).completeDrugReturn("return-2", "PUBLIC_SCAN", "refund-2");
        verify(auditPublisher).publish(
                eq("PAYMENT_REFUND"),
                eq("REFUND"),
                eq("refund-2"),
                eq("patient-1"),
                eq("pres-2"),
                eq("PUBLIC_SCAN"),
                eq("SYSTEM"),
                any());
    }

    @Test
    void qrCodeRejectsWhenTestModeDisabled() {
        CashierController controller = controller(false, "");

        assertThatThrownBy(() -> controller.qrCode("https://example.com/pay/123", null, null, request))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void qrCodeRendersSvgForExplicitTarget() {
        CashierController controller = controller(true, "");

        var response = controller.qrCode("https://example.com/pay/123", null, null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("image/svg+xml"));
        assertThat(response.getBody()).contains("<svg");
        assertThat(response.getBody()).contains("viewBox=");
    }

    @Test
    void refundQrCodeUsesConfiguredPublicBaseUrl() {
        CashierController controller = controller(true, "https://cashier.example/");

        var response = controller.refundQrCode(
                "PRESCRIPTION",
                "pres-9",
                "patient-9",
                "return-9",
                new BigDecimal("9.99"),
                "ALIPAY",
                request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("image/svg+xml"));
        assertThat(response.getBody()).contains("<svg");
    }

    @Test
    void qrCodeRejectsBlankTargetWhenPaymentIdMissing() {
        CashierController controller = controller(true, "");

        assertThatThrownBy(() -> controller.qrCode(" ", null, null, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("paymentId must not be blank");
    }

    @org.junit.jupiter.api.AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private CashierController controller(boolean testModeEnabled, String publicScanBaseUrl) {
        return new CashierController(
                repository,
                appointmentClient,
                medicalOrderClient,
                prescriptionClient,
                patientAccessClient,
                auditPublisher,
                testModeEnabled,
                publicScanBaseUrl);
    }

    private JwtAuthenticationToken authentication(String subject, String role) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", subject)
                .claim("role", role)
                .build();
        return new JwtAuthenticationToken(jwt);
    }

    private CashierRepository.Payment payment(
            String id,
            String businessType,
            String businessId,
            String patientId,
            String status,
            String method) {
        return new CashierRepository.Payment(
                id,
                businessType,
                businessId,
                patientId,
                new BigDecimal("0.01"),
                method,
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
                BigDecimal.TEN,
                "reason",
                "REFUNDED",
                "operator-1",
                LocalDateTime.of(2026, 7, 9, 10, 30));
    }
}
