package com.cloudbrain.medicalorder.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.medicalorder.domain.MedicalOrder;
import com.cloudbrain.medicalorder.service.MedicalOrderService;
import com.cloudbrain.medicalorder.service.PatientAccessClient;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class MedicalOrderControllerTest {
    private final MedicalOrderService service = Mockito.mock(MedicalOrderService.class);
    private final PatientAccessClient patientAccessClient = Mockito.mock(PatientAccessClient.class);
    private final MedicalOrderController controller = new MedicalOrderController(service, patientAccessClient);

    @Test
    void createAndOperationalEndpointsDelegateToService() {
        MedicalOrder order = order("order-1", "CHECK", "patient-1", "doctor-1", "WAITING");
        when(service.create(any(), org.mockito.ArgumentMatchers.eq("doctor-1"))).thenReturn(order);
        when(service.pay("order-1", "cashier-1", "CASHIER")).thenReturn(order);
        when(service.call("order-1", "checker-1", "CHECK_DOCTOR")).thenReturn(order);
        when(service.start("order-1", "checker-1", "CHECK_DOCTOR")).thenReturn(order);
        when(service.complete("order-1", "checker-1", "CHECK_DOCTOR", "done", "HUMAN", null)).thenReturn(order);
        when(service.markReportPending("order-1", "checker-1", "CHECK_DOCTOR", "pending")).thenReturn(order);
        when(service.miss("order-1", "checker-1", "CHECK_DOCTOR")).thenReturn(order);

        assertThat(controller.create(
                        new MedicalOrderController.CreateRequest(
                                "appt-1",
                                "patient-1",
                                "Patient",
                                "CHECK",
                                "CT",
                                "CT",
                                "purpose",
                                "HEAD",
                                BigDecimal.TEN,
                                "ROUTINE"),
                        auth("doctor-1", "OUTPATIENT_DOCTOR")))
                .isEqualTo(order);
        assertThat(controller.pay("order-1", auth("cashier-1", "CASHIER"))).isEqualTo(order);
        assertThat(controller.call("order-1", auth("checker-1", "CHECK_DOCTOR"))).isEqualTo(order);
        assertThat(controller.start("order-1", auth("checker-1", "CHECK_DOCTOR"))).isEqualTo(order);
        assertThat(controller.complete(
                        "order-1",
                        new MedicalOrderController.CompleteRequest("done", "HUMAN", null),
                        auth("checker-1", "CHECK_DOCTOR")))
                .isEqualTo(order);
        assertThat(controller.reportPending(
                        "order-1",
                        new MedicalOrderController.ReportPendingRequest("pending"),
                        auth("checker-1", "CHECK_DOCTOR")))
                .isEqualTo(order);
        assertThat(controller.miss("order-1", auth("checker-1", "CHECK_DOCTOR"))).isEqualTo(order);
    }

    @Test
    void patientListBindsDefaultPatientIdAndDelegatesAuthorizedQuery() {
        MedicalOrder order = order("order-1", "CHECK", "patient-1", "doctor-1", "WAITING");
        when(patientAccessClient.boundPatientId("account-1")).thenReturn("patient-1");
        when(patientAccessClient.owns("account-1", "patient-1")).thenReturn(true);
        when(service.listAuthorized(null, null, "patient-1", null, "payment_record", "account-1", "PATIENT"))
                .thenReturn(List.of(order));

        assertThat(controller.list(null, null, null, null, "payment_record", auth("account-1", "PATIENT")))
                .containsExactly(order);
    }

    @Test
    void patientListRejectsUnboundOrForeignPatient() {
        when(patientAccessClient.boundPatientId("account-1")).thenReturn("");
        when(patientAccessClient.owns("account-1", "patient-2")).thenReturn(false);

        assertThatThrownBy(() -> controller.list(null, null, null, null, null, auth("account-1", "PATIENT")))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.list(null, null, "patient-2", null, null, auth("account-1", "PATIENT")))
                .isInstanceOf(AccessDeniedException.class);
    }

    private JwtAuthenticationToken auth(String subject, String role) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("role", role)
                .build();
        return new JwtAuthenticationToken(jwt);
    }

    private MedicalOrder order(String id, String type, String patientId, String doctorId, String status) {
        return new MedicalOrder(
                id,
                "appt-1",
                patientId,
                "Patient",
                doctorId,
                type,
                "ITEM",
                "Item",
                "purpose",
                "HEAD",
                BigDecimal.TEN,
                "PAID",
                status,
                "room-1",
                "Room 1",
                "Floor 1",
                "staff-1",
                1,
                "ROUTINE",
                null,
                null,
                0,
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.now(),
                null,
                null);
    }
}
