package com.cloudbrain.appointment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.appointment.entity.Appointment;
import com.cloudbrain.appointment.entity.AppointmentSource;
import com.cloudbrain.appointment.entity.AppointmentStatus;
import com.cloudbrain.appointment.entity.PaymentStatus;
import com.cloudbrain.appointment.entity.SlotInventory;
import com.cloudbrain.appointment.service.AppointmentService;
import com.cloudbrain.appointment.service.PatientVerificationClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class AppointmentControllerAdditionalTest {
    @Mock AppointmentService appointmentService;
    @Mock PatientVerificationClient patientVerificationClient;

    @Test
    void patientListUsesBoundPatientWhenNoPatientIdProvided() {
        Appointment appointment = appointment("appt", "patient-1", AppointmentStatus.WAITING);
        when(patientVerificationClient.boundPatientId("account-1")).thenReturn("patient-1");
        when(patientVerificationClient.owns("account-1", "patient-1")).thenReturn(true);
        when(appointmentService.list(null, "patient-1", null)).thenReturn(List.of(appointment));
        AppointmentController controller = new AppointmentController(appointmentService, patientVerificationClient);

        assertThat(controller.list(null, null, null, authentication("account-1", "PATIENT")))
                .containsExactly(appointment);
    }

    @Test
    void patientListRejectsWhenNoBoundPatientExists() {
        when(patientVerificationClient.boundPatientId("account-1")).thenReturn(null);
        AppointmentController controller = new AppointmentController(appointmentService, patientVerificationClient);

        assertThatThrownBy(() -> controller.list(null, null, null, authentication("account-1", "PATIENT")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void patientListRejectsOtherPatientsData() {
        when(patientVerificationClient.owns("account-1", "patient-2")).thenReturn(false);
        AppointmentController controller = new AppointmentController(appointmentService, patientVerificationClient);

        assertThatThrownBy(() -> controller.list(null, "patient-2", "WAITING", authentication("account-1", "PATIENT")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void slotsTodayQueueAndSyncSlotDelegateToService() {
        SlotInventory inventory = new SlotInventory("slot-1", 10, 2);
        Appointment appointment = appointment("appt", "patient-1", AppointmentStatus.WAITING);
        when(appointmentService.slots()).thenReturn(List.of(inventory));
        when(appointmentService.todayQueue("doctor-1")).thenReturn(List.of(appointment));
        when(appointmentService.syncSlot("slot-1", 8)).thenReturn(inventory);
        AppointmentController controller = new AppointmentController(appointmentService, patientVerificationClient);

        assertThat(controller.slots()).containsExactly(inventory);
        assertThat(controller.todayQueue(authentication("doctor-1", "OUTPATIENT_DOCTOR"))).containsExactly(appointment);
        assertThat(controller.syncSlot(new AppointmentController.SyncSlotRequest("slot-1", 8))).isSameAs(inventory);
    }

    @Test
    void lockOnlineChecksBindingAndOwnership() {
        Appointment appointment = appointment("appt", "patient-1", AppointmentStatus.PENDING_PAYMENT);
        AppointmentController.CreateAppointmentRequest request = request("patient-1");
        when(patientVerificationClient.hasBoundPatient("account-1")).thenReturn(true);
        when(patientVerificationClient.owns("account-1", "patient-1")).thenReturn(true);
        when(appointmentService.lockOnline(request)).thenReturn(appointment);
        AppointmentController controller = new AppointmentController(appointmentService, patientVerificationClient);

        assertThat(controller.lockOnline(request, authentication("account-1", "PATIENT"))).isSameAs(appointment);

        when(patientVerificationClient.hasBoundPatient("account-2")).thenReturn(false);
        assertThatThrownBy(() -> controller.lockOnline(request, authentication("account-2", "PATIENT")))
                .isInstanceOf(AccessDeniedException.class);

        when(patientVerificationClient.hasBoundPatient("account-3")).thenReturn(true);
        when(patientVerificationClient.owns("account-3", "patient-1")).thenReturn(false);
        assertThatThrownBy(() -> controller.lockOnline(request, authentication("account-3", "PATIENT")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createOfflineRequiresVerifiedPatient() {
        AppointmentController controller = new AppointmentController(appointmentService, patientVerificationClient);
        AppointmentController.CreateAppointmentRequest request = request("patient-1");
        when(patientVerificationClient.isVerified("patient-1")).thenReturn(false);

        assertThatThrownBy(() -> controller.createOffline(request)).isInstanceOf(AccessDeniedException.class);

        Appointment appointment = appointment("appt", "patient-1", AppointmentStatus.PENDING_PAYMENT);
        when(patientVerificationClient.isVerified("patient-1")).thenReturn(true);
        when(appointmentService.createOffline(request)).thenReturn(appointment);
        assertThat(controller.createOffline(request)).isSameAs(appointment);
    }

    @Test
    void payCancelSkipCallStartAndUpdateStatusDelegate() {
        Appointment paid = appointment("appt", "patient-1", AppointmentStatus.WAITING);
        Appointment cancelled = appointment("appt", "patient-1", AppointmentStatus.CANCELLED);
        AppointmentController controller = new AppointmentController(appointmentService, patientVerificationClient);
        AppointmentController.PayRequest payRequest = new AppointmentController.PayRequest("WECHAT", new BigDecimal("12.30"), "ignored");
        when(appointmentService.pay("appt", "WECHAT", new BigDecimal("12.30"), "cashier-1")).thenReturn(paid);
        when(appointmentService.find("appt")).thenReturn(appointment("appt", "patient-1", AppointmentStatus.WAITING));
        when(patientVerificationClient.owns("account-1", "patient-1")).thenReturn(true);
        when(appointmentService.cancel("appt", false)).thenReturn(cancelled);
        when(appointmentService.cancel("appt", true)).thenReturn(cancelled);
        when(appointmentService.skip("appt", "doctor-1")).thenReturn(paid);
        when(appointmentService.call("appt", "doctor-1")).thenReturn(paid);
        when(appointmentService.startVisit("appt", "doctor-1")).thenReturn(paid);
        when(appointmentService.updateStatus("appt", "FINISHED", "doctor-1")).thenReturn(paid);

        assertThat(controller.pay("appt", payRequest, authentication("cashier-1", "CASHIER"))).isSameAs(paid);
        assertThat(controller.cancel("appt", authentication("account-1", "PATIENT"))).isSameAs(cancelled);
        assertThat(controller.cancel("appt", authentication("cashier-1", "CASHIER"))).isSameAs(cancelled);
        assertThat(controller.skip("appt", authentication("doctor-1", "OUTPATIENT_DOCTOR"))).isSameAs(paid);
        assertThat(controller.call("appt", authentication("doctor-1", "OUTPATIENT_DOCTOR"))).isSameAs(paid);
        assertThat(controller.start("appt", authentication("doctor-1", "OUTPATIENT_DOCTOR"))).isSameAs(paid);
        assertThat(controller.updateStatus("appt", Map.of("status", "FINISHED"), authentication("doctor-1", "OUTPATIENT_DOCTOR")))
                .isSameAs(paid);
    }

    @Test
    void updateStatusFallsBackToWaitingWhenBodyMissingStatus() {
        AppointmentController controller = new AppointmentController(appointmentService, patientVerificationClient);
        Appointment appointment = appointment("appt", "patient-1", AppointmentStatus.WAITING);
        when(appointmentService.updateStatus("appt", "WAITING", "doctor-1")).thenReturn(appointment);

        assertThat(controller.updateStatus("appt", Map.of(), authentication("doctor-1", "OUTPATIENT_DOCTOR")))
                .isSameAs(appointment);
    }

    @Test
    void patientCancelRejectsUnauthorizedOwnership() {
        AppointmentController controller = new AppointmentController(appointmentService, patientVerificationClient);
        when(appointmentService.find("appt")).thenReturn(appointment("appt", "patient-1", AppointmentStatus.WAITING));
        when(patientVerificationClient.owns("account-1", "patient-1")).thenReturn(false);

        assertThatThrownBy(() -> controller.cancel("appt", authentication("account-1", "PATIENT")))
                .isInstanceOf(AccessDeniedException.class);
        verify(appointmentService, never()).cancel(anyString(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void revisitSkipsClinicHoursCheckForDifferentVisitDateAndCoversTimeHelpers() {
        AppointmentController controller = new AppointmentController(appointmentService, patientVerificationClient);
        Appointment differentDay = new Appointment(
                "appt",
                "slot-1",
                "patient-1",
                "Patient",
                "doctor-1",
                "Doctor",
                "dept-1",
                "Dept",
                LocalDate.now().minusDays(1),
                "MORNING",
                LocalTime.of(8, 0),
                AppointmentSource.ONLINE,
                AppointmentStatus.FINISHED,
                PaymentStatus.PAID,
                null,
                "LOW",
                null,
                1);
        Appointment revisit = appointment("appt", "patient-1", AppointmentStatus.REVISIT_WAITING);
        when(appointmentService.find("appt")).thenReturn(differentDay);
        when(patientVerificationClient.owns("account-1", "patient-1")).thenReturn(true);
        when(appointmentService.enterRevisit("appt")).thenReturn(revisit);

        assertThat(controller.revisit("appt", authentication("account-1", "PATIENT"))).isSameAs(revisit);
        assertThat(controller.currentDate()).isNotNull();
        assertThat(controller.currentTime()).isNotNull();
    }

    private JwtAuthenticationToken authentication(String subject, String role) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", subject)
                .claim("role", role)
                .build();
        return new JwtAuthenticationToken(jwt);
    }

    private Appointment appointment(String id, String patientId, AppointmentStatus status) {
        return new Appointment(
                id,
                "slot-1",
                patientId,
                "Patient",
                "doctor-1",
                "Doctor",
                "dept-1",
                "Dept",
                LocalDate.now().plusDays(1),
                "MORNING",
                LocalTime.of(8, 0),
                AppointmentSource.ONLINE,
                status,
                status == AppointmentStatus.PENDING_PAYMENT ? PaymentStatus.UNPAID : PaymentStatus.PAID,
                null,
                "LOW",
                null,
                1);
    }

    private AppointmentController.CreateAppointmentRequest request(String patientId) {
        return new AppointmentController.CreateAppointmentRequest(
                "slot-1",
                patientId,
                "Patient",
                "doctor-1",
                "Doctor",
                "dept-1",
                "Dept",
                LocalDate.now().plusDays(1).toString(),
                "MORNING",
                "08:00",
                null,
                "LOW",
                null,
                new BigDecimal("12.30"));
    }
}
