package com.cloudbrain.cashier.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;

@ExtendWith(MockitoExtension.class)
class CashierControllerAdditionalTest {
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
    void refundsUseBoundPatientForPatientRole() {
        CashierController controller = controller(true, "");
        List<CashierRepository.Refund> refunds = List.of(refund("refund-1", "PRESCRIPTION", "biz-1", "patient-1"));
        when(patientAccessClient.boundPatientId("account-1")).thenReturn("patient-1");
        when(patientAccessClient.owns("account-1", "patient-1")).thenReturn(true);
        when(repository.refunds("patient-1", "biz-1")).thenReturn(refunds);

        List<CashierRepository.Refund> result = controller.refunds(" ", "biz-1", authentication("account-1", "PATIENT"));

        assertThat(result).isSameAs(refunds);
        verify(repository).refunds("patient-1", "biz-1");
    }

    @Test
    void createOrderRejectsNegativeAmount() {
        CashierController controller = controller(true, "");

        assertThatThrownBy(() -> controller.createOrder(
                new CashierController.CreatePaymentRequest("APPOINTMENT", "biz-1", "patient-1", new BigDecimal("-0.01"), "WECHAT"),
                authentication("cashier-1", "CASHIER")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scanEntryReturnsAlreadyProcessedMessageWithoutConfirmingAgain() {
        CashierController controller = controller(true, "");
        CashierRepository.Payment before = payment("payment-1", "APPOINTMENT", "biz-1", "patient-1", "PAID", "WECHAT_TEST");
        CashierRepository.Payment after = payment("payment-1", "APPOINTMENT", "biz-1", "patient-1", "PAID", "WECHAT_TEST");
        when(repository.findById("payment-1")).thenReturn(before);
        when(repository.recordTestPaymentById(
                org.mockito.ArgumentMatchers.eq("payment-1"),
                org.mockito.ArgumentMatchers.eq("WECHAT"),
                org.mockito.ArgumentMatchers.eq("PUBLIC_SCAN"),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(after);

        var response = controller.scanEntry("payment-1", "WECHAT");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("无需重复扫码");
        verify(appointmentClient, never()).confirm(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(auditPublisher, never()).publish(
                org.mockito.ArgumentMatchers.eq("PAYMENT_CONFIRMED"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refundScanEntrySupportsAppointmentRefunds() {
        CashierController controller = controller(true, "");

        var response = controller.refundScanEntry("APPOINTMENT", "appt-1", "patient-1", null, null, "WECHAT");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("appointment cancellation and refund are now synced");
        verify(appointmentClient).refund("appt-1", "patient-1", "PUBLIC_SCAN");
    }

    @Test
    void normalizeChannelRejectsUnsupportedPublicScanChannel() throws Exception {
        CashierController controller = controller(true, "");

        assertThatThrownBy(() -> invoke(
                controller,
                "normalizeChannel",
                new Class<?>[] {String.class, boolean.class},
                "SIMULATED",
                false))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizeChannelAllowsSimulatedOnlyForCallbacks() throws Exception {
        CashierController controller = controller(true, "");

        Object normalized = invoke(
                controller,
                "normalizeChannel",
                new Class<?>[] {String.class, boolean.class},
                "SIMULATED",
                true);

        assertThat(normalized).isEqualTo("SIMULATED");
    }

    @Test
    void refundDrugReturnRejectsMissingReturnId() {
        CashierController controller = controller(true, "");

        assertThatThrownBy(() -> controller.refundDrugReturn(
                new CashierController.DrugReturnRefundRequest(" ", "pres-1", "patient-1", BigDecimal.ONE, "reason"),
                authentication("cashier-1", "CASHIER")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("returnId");
    }

    @Test
    void refundDrugReturnRejectsWhenPrescriptionPaymentIsNotRefundable() {
        CashierController controller = controller(true, "");
        when(repository.findOptionalByBusiness("PRESCRIPTION", "pres-1"))
                .thenReturn(java.util.Optional.of(payment("payment-1", "PRESCRIPTION", "pres-1", "patient-1", "FAILED", "WECHAT_TEST")));

        assertThatThrownBy(() -> controller.refundDrugReturn(
                new CashierController.DrugReturnRefundRequest("return-1", "pres-1", "patient-1", BigDecimal.ONE, "reason"),
                authentication("cashier-1", "CASHIER")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testCallbackRejectsBlankBusinessIdentifiers() {
        CashierController controller = controller(true, "");

        assertThatThrownBy(() -> controller.testCallback(
                new CashierController.TestPaymentRequest(" ", " ", "patient-1", "WECHAT", null),
                authentication("cashier-1", "CASHIER")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("businessType");
    }

    @Test
    void testCallbackConfirmsPrescriptionPayment() {
        CashierController controller = controller(true, "");
        CashierRepository.Payment payment = payment("payment-8", "PRESCRIPTION", "pres-8", "patient-1", "PAID", "ALIPAY_TEST");
        when(repository.recordTestPayment("PRESCRIPTION", "pres-8", "patient-1", "ALIPAY", "cashier-1", "trade-8"))
                .thenReturn(payment);

        CashierRepository.Payment result = controller.testCallback(
                new CashierController.TestPaymentRequest("PRESCRIPTION", "pres-8", "patient-1", "ALIPAY", "trade-8"),
                authentication("cashier-1", "CASHIER"));

        assertThat(result).isSameAs(payment);
        verify(prescriptionClient).confirm("pres-8", "patient-1", "payment-8");
    }

    @Test
    void qrCodeRejectsOversizedExplicitTarget() {
        CashierController controller = controller(true, "");
        String target = "x".repeat(2049);

        assertThatThrownBy(() -> controller.qrCode(target, null, null, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void helperMethodsCoverRemainingBranchCases() throws Exception {
        CashierController controller = controller(true, "");
        when(request.getScheme()).thenReturn("https");
        when(request.getServerName()).thenReturn("cashier.local");
        when(request.getServerPort()).thenReturn(8443);
        when(request.getContextPath()).thenReturn("/cashier");

        assertThat(invoke(controller, "errorMessage", new Class<?>[] {Exception.class, String.class},
                new RuntimeException(), "fallback")).isEqualTo("fallback");
        assertThat(invoke(controller, "errorMessage", new Class<?>[] {Exception.class, String.class},
                new IllegalStateException("boom"), "fallback")).isEqualTo("boom");
        assertThat(invoke(controller, "channelLabel", new Class<?>[] {String.class}, (Object) null)).isEqualTo("扫码支付");
        assertThat(invoke(controller, "channelLabel", new Class<?>[] {String.class}, "ALIPAY")).isEqualTo("支付宝支付");
        assertThat(invoke(controller, "channelLabel", new Class<?>[] {String.class}, "MEDICAL_INSURANCE")).isEqualTo("医保卡支付");
        assertThat(invoke(controller, "channelLabel", new Class<?>[] {String.class}, "SIMULATED")).isEqualTo("模拟支付");
        assertThat(invoke(controller, "channelLabel", new Class<?>[] {String.class}, "BANK_CARD")).isEqualTo("BANK_CARD");
        assertThat(invoke(controller, "refundChannelLabel", new Class<?>[] {String.class}, (Object) null)).isEqualTo("Refund QR");
        assertThat(invoke(controller, "refundChannelLabel", new Class<?>[] {String.class}, "ALIPAY"))
                .isEqualTo("Alipay Refund Code");
        assertThat(invoke(controller, "refundChannelLabel", new Class<?>[] {String.class}, "MEDICAL_INSURANCE"))
                .isEqualTo("Insurance Refund Code");
        assertThat(invoke(controller, "refundChannelLabel", new Class<?>[] {String.class}, "BANK_CARD"))
                .isEqualTo("BANK_CARD");
        assertThat(invoke(controller, "normalizeBaseUrl", new Class<?>[] {String.class}, (Object) null)).isEqualTo("");
        assertThat(invoke(controller, "normalizeBaseUrl", new Class<?>[] {String.class}, "https://cashier.example///"))
                .isEqualTo("https://cashier.example");
        assertThat(invoke(controller, "resolveScanBaseUrl", new Class<?>[] {HttpServletRequest.class}, request))
                .isEqualTo("https://cashier.local:8443/cashier");
        assertThat(invoke(controller, "resolveQrContent",
                new Class<?>[] {String.class, String.class, String.class, HttpServletRequest.class},
                null, "payment-9", "ALIPAY", request))
                .isEqualTo("https://cashier.local:8443/cashier/api/payments/scan-entry?paymentId=payment-9&channel=ALIPAY");
        String errorPage = (String) invoke(controller, "scanResultPage",
                new Class<?>[] {boolean.class, String.class, String.class},
                false, "ALIPAY", "<unsafe>");
        assertThat(errorPage).contains("&lt;unsafe&gt;");
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private Object invoke(CashierController controller, String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = CashierController.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(controller, args);
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
