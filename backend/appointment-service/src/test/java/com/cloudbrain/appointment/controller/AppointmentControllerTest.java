package com.cloudbrain.appointment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.appointment.entity.Appointment;
import com.cloudbrain.appointment.entity.AppointmentSource;
import com.cloudbrain.appointment.entity.AppointmentStatus;
import com.cloudbrain.appointment.entity.PaymentStatus;
import com.cloudbrain.appointment.service.AppointmentService;
import com.cloudbrain.appointment.service.PatientVerificationClient;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class AppointmentControllerTest {
    private static final LocalDate VISIT_DATE = LocalDate.of(2026, 7, 6);

    @Mock
    AppointmentService appointmentService;

    @Mock
    PatientVerificationClient patientVerificationClient;

    @Test
    void patientRevisitAfterHoursShowsBusinessPrompt() {
        AppointmentController controller = spy(new AppointmentController(appointmentService, patientVerificationClient));
        Appointment appointment = appointment(AppointmentStatus.FINISHED);
        when(appointmentService.find("appt")).thenReturn(appointment);
        when(patientVerificationClient.owns("account-1", "patient-1")).thenReturn(true);
        doReturn(VISIT_DATE).when(controller).currentDate();
        doReturn(LocalTime.of(20, 0)).when(controller).currentTime();

        assertThatThrownBy(() -> controller.revisit("appt", authentication("account-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("08:00-12:00");

        verify(appointmentService, never()).enterRevisit(anyString());
    }

    @Test
    void patientRevisitDuringClinicHoursCanProceed() {
        AppointmentController controller = spy(new AppointmentController(appointmentService, patientVerificationClient));
        Appointment appointment = appointment(AppointmentStatus.FINISHED);
        Appointment revisit = appointment(AppointmentStatus.REVISIT_WAITING);
        when(appointmentService.find("appt")).thenReturn(appointment);
        when(appointmentService.enterRevisit("appt")).thenReturn(revisit);
        when(patientVerificationClient.owns("account-1", "patient-1")).thenReturn(true);
        doReturn(VISIT_DATE).when(controller).currentDate();
        doReturn(LocalTime.of(10, 0)).when(controller).currentTime();

        assertThat(controller.revisit("appt", authentication("account-1"))).isSameAs(revisit);
    }

    private JwtAuthenticationToken authentication(String subject) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", subject)
                .claim("role", "PATIENT")
                .build();
        return new JwtAuthenticationToken(jwt);
    }

    private Appointment appointment(AppointmentStatus status) {
        return new Appointment(
                "appt",
                "slot-am-1000",
                "patient-1",
                "Patient",
                "doctor-1",
                "Doctor",
                "dept-1",
                "Department",
                VISIT_DATE,
                "上午",
                LocalTime.of(10, 0),
                AppointmentSource.ONLINE,
                status,
                PaymentStatus.PAID,
                null,
                "LOW",
                null,
                108);
    }
}
