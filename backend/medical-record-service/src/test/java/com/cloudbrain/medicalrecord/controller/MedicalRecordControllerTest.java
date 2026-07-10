package com.cloudbrain.medicalrecord.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.medicalrecord.entity.MedicalRecord;
import com.cloudbrain.medicalrecord.repository.MedicalRecordRepository;
import com.cloudbrain.medicalrecord.service.MedicalRecordService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MedicalRecordControllerTest {
    @Mock
    MedicalRecordService service;

    @Test
    void listAndHistoryDelegateAuthenticatedIdentity() {
        MedicalRecordController controller = new MedicalRecordController(service, "internal-key");
        MedicalRecord record = record("appointment-1");
        JwtAuthenticationToken patient = token("patient-user", "PATIENT");
        JwtAuthenticationToken doctor = token("doctor-1", "OUTPATIENT_DOCTOR");
        when(service.listAuthorized("patient-user", "PATIENT", "patient-1", null, "ACTIVE"))
                .thenReturn(List.of(record));
        when(service.history("patient-1", "appointment-1", "follow-up", "doctor-1"))
                .thenReturn(List.of(record));

        assertThat(controller.list("patient-1", null, "ACTIVE", patient)).containsExactly(record);
        assertThat(controller.history("patient-1", "appointment-1", "follow-up", doctor)).containsExactly(record);
    }

    @Test
    void internalEndpointsRequireKey() {
        MedicalRecordController controller = new MedicalRecordController(service, "internal-key");
        MedicalRecordController.CreateInitialRecordRequest request =
                new MedicalRecordController.CreateInitialRecordRequest(
                        "appointment-1", "patient-1", "Alice", "doctor-1", "Doctor", "Dept", "2026-07-10", "AM", "summary", "LOW");

        assertThatThrownBy(() -> controller.createInitial(request, "bad-key"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.saved("appointment-1", "bad-key"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.linkReport(
                "appointment-1",
                "bad-key",
                new MedicalRecordController.ReportLink("order-1", "report-1", "LAB", "ok", "doctor", LocalDateTime.now())))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void controllerDelegatesRemainingEndpoints() {
        MedicalRecordController controller = new MedicalRecordController(service, "internal-key");
        MedicalRecord record = record("appointment-1");
        JwtAuthenticationToken doctor = token("doctor-1", "OUTPATIENT_DOCTOR");
        MedicalRecordController.CreateInitialRecordRequest createRequest =
                new MedicalRecordController.CreateInitialRecordRequest(
                        "appointment-1", "patient-1", "Alice", "ignored", "Doctor", "Dept", "2026-07-10", "AM", "summary", "LOW");
        MedicalRecordController.WriteDoctorNoteRequest noteRequest =
                new MedicalRecordController.WriteDoctorNoteRequest(
                        "appointment-1", 1L, "chief", "present", "past", "allergy", "exam",
                        "diagnosis", "plan", "", "HUMAN", null);
        MedicalRecordRepository.AccessLog accessLog =
                new MedicalRecordRepository.AccessLog(1L, "record-1", "patient-1", "doctor-1", "OUTPATIENT_DOCTOR", "LIST", "reason",
                        LocalDateTime.parse("2026-07-10T10:00:00"));
        when(service.createInitial(createRequest)).thenReturn(record);
        when(service.createInitial(new MedicalRecordController.CreateInitialRecordRequest(
                "appointment-1", "patient-1", "Alice", "doctor-1", "Doctor", "Dept", "2026-07-10", "AM", "summary", "LOW")))
                .thenReturn(record);
        when(service.isSaved("appointment-1")).thenReturn(true);
        when(service.writeDoctorNote(noteRequest, "doctor-1")).thenReturn(record);
        when(service.archive("record-1", "doctor-1")).thenReturn(record);
        when(service.accessLogs("patient-1")).thenReturn(List.of(accessLog));

        assertThat(controller.createInitial(createRequest, "internal-key")).isEqualTo(record);
        assertThat(controller.doctorInit(createRequest, doctor)).isEqualTo(record);
        assertThat(controller.saved("appointment-1", "internal-key")).isEqualTo(Map.of("saved", true));
        controller.linkReport(
                "appointment-1",
                "internal-key",
                new MedicalRecordController.ReportLink("order-1", "report-1", "LAB", "ok", "doctor", LocalDateTime.parse("2026-07-10T10:10:00")));
        assertThat(controller.writeDoctorNote(noteRequest, doctor)).isEqualTo(record);
        assertThat(controller.archive("record-1", doctor)).isEqualTo(record);
        assertThat(controller.accessLogs("patient-1")).containsExactly(accessLog);
    }

    private JwtAuthenticationToken token(String subject, String role) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", subject)
                .claim("role", role)
                .build();
        return new JwtAuthenticationToken(jwt);
    }

    private MedicalRecord record(String appointmentId) {
        return new MedicalRecord(
                "record-" + appointmentId,
                appointmentId,
                "patient-1",
                "Alice",
                "doctor-1",
                "Doctor",
                "Dept",
                "2026-07-10",
                "AM",
                "summary",
                "LOW");
    }
}
