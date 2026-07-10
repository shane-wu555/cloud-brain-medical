package com.cloudbrain.medicalrecord.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.medicalrecord.audit.AuditPublisher;
import com.cloudbrain.medicalrecord.controller.MedicalRecordController;
import com.cloudbrain.medicalrecord.entity.MedicalRecord;
import com.cloudbrain.medicalrecord.entity.MedicalRecordStatus;
import com.cloudbrain.medicalrecord.repository.MedicalRecordRepository;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MedicalRecordServiceTest {
    @Mock
    MedicalRecordRepository repository;
    @Mock
    PatientAccessClient patientAccessClient;
    @Mock
    AuditPublisher auditPublisher;

    @Test
    void duplicateInitialEventReturnsExistingRecordWithoutOverwritingDoctorContent() {
        MedicalRecord existing = new MedicalRecord(
                "r", "a", "p", "patient", "d", "doctor", "dept", "2026-06-23", "AM", "summary", "LOW");
        when(repository.findByAppointmentId("a")).thenReturn(Optional.of(existing));

        MedicalRecord result = service().createInitial(request());

        assertThat(result).isSameAs(existing);
        verify(repository, never()).createInitialIfAbsent(any());
    }

    @Test
    void createInitialCreatesRecordWhenAbsent() {
        MedicalRecord created = record("new");
        when(repository.findByAppointmentId("new")).thenReturn(Optional.empty());
        when(repository.createInitialIfAbsent(any())).thenReturn(created);

        MedicalRecord result = service().createInitial(new MedicalRecordController.CreateInitialRecordRequest(
                "new", "p", "patient", "d", "doctor", "dept", "2026-06-23", "AM", "summary", "LOW"));

        assertThat(result).isSameAs(created);
        verify(repository).createInitialIfAbsent(any());
    }

    @Test
    void staleVersionIsRejectedBeforeSaving() {
        MedicalRecord existing = record("a");
        when(repository.findByAppointmentId("a")).thenReturn(Optional.of(existing));
        var request = new MedicalRecordController.WriteDoctorNoteRequest(
                "a", 1, "chief", "present", "past", "allergy", "exam",
                "diagnosis", "plan", "", "HUMAN", null);

        assertThatThrownBy(() -> service().writeDoctorNote(request, "d"))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
        verify(repository, never()).save(any(), any(Long.class));
    }

    @Test
    void historicalAccessRequiresReasonAndWritesAudit() {
        MedicalRecord current = record("current");
        MedicalRecord old = record("old");
        when(repository.findByAppointmentId("current")).thenReturn(Optional.of(current));
        when(repository.findByPatientIdExcludingCancelledAppointments("p"))
                .thenReturn(java.util.List.of(current, old));

        var result = service().history("p", "current", "follow-up", "d");

        assertThat(result).containsExactly(old);
        verify(repository).recordAccess(old.getId(), "p", "d", "OUTPATIENT_DOCTOR", "HISTORY", "follow-up");
        verify(auditPublisher).publish(
                "MEDICAL_RECORD_HISTORY_VIEW",
                "MEDICAL_RECORD",
                null,
                "p",
                "current",
                "d",
                "OUTPATIENT_DOCTOR",
                Map.of("accessScope", "HISTORY", "reason", "follow-up", "resultCount", 1));
    }

    @Test
    void listAuthorizedAuditsSingleAggregatedListAccess() {
        MedicalRecord first = record("a1");
        MedicalRecord second = record("a2");
        when(repository.findByPatientIdExcludingCancelledAppointments("p")).thenReturn(java.util.List.of(first, second));
        when(patientAccessClient.owns("patient-user", "p")).thenReturn(true);

        var result = service().listAuthorized("patient-user", "PATIENT", "p", null, null);

        assertThat(result).containsExactly(first, second);
        verify(auditPublisher).publish(
                "MEDICAL_RECORD_LIST_VIEW",
                "MEDICAL_RECORD",
                null,
                "p",
                null,
                "patient-user",
                "PATIENT",
                Map.of("accessScope", "LIST", "statusFilter", "ALL", "resultCount", 2));
    }

    @Test
    void appointmentScopedQueryAuditsAsDetailAccess() {
        MedicalRecord record = record("a1");
        when(repository.findByAppointmentId("a1")).thenReturn(Optional.of(record));

        var result = service().listAuthorized("d", "OUTPATIENT_DOCTOR", null, "a1", null);

        assertThat(result).containsExactly(record);
        verify(auditPublisher).publish(
                "MEDICAL_RECORD_DETAIL_VIEW",
                "MEDICAL_RECORD",
                "r-a1",
                null,
                "a1",
                "d",
                "OUTPATIENT_DOCTOR",
                Map.of("accessScope", "APPOINTMENT", "statusFilter", "ALL", "resultCount", 1));
    }

    @Test
    void patientAccessRequiresBoundOwnedProfile() {
        when(patientAccessClient.boundPatientId("patient-user")).thenReturn(null);

        assertThatThrownBy(() -> service().listAuthorized("patient-user", "PATIENT", null, null, null))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        when(patientAccessClient.boundPatientId("patient-user")).thenReturn("patient-1");
        when(patientAccessClient.owns("patient-user", "patient-1")).thenReturn(false);

        assertThatThrownBy(() -> service().listAuthorized("patient-user", "PATIENT", null, null, null))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void doctorCannotSeeAnotherDoctorsRecordAndUnsupportedRoleIsRejected() {
        MedicalRecord record = record("a1");
        when(repository.findByAppointmentId("a1")).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service().listAuthorized("doctor-x", "OUTPATIENT_DOCTOR", null, "a1", null))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        assertThatThrownBy(() -> service().listAuthorized("admin-1", "ADMIN", null, null, null))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void historyRejectsBlankReasonAndWrongDoctor() {
        MedicalRecord current = record("current");
        when(repository.findByAppointmentId("current")).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service().history("p", "current", " ", "d"))
                .isInstanceOf(IllegalArgumentException.class);

        when(repository.findByAppointmentId("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().history("p", "missing", "follow-up", "d"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service().history("p", "current", "follow-up", "doctor-x"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void writeDoctorNoteValidatesSourceAndPublishesAiConfirmation() {
        MedicalRecord record = record("a2");
        when(repository.findByAppointmentId("a2")).thenReturn(Optional.of(record));
        when(repository.save(any(), eq(0L))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service().writeDoctorNote(
                new MedicalRecordController.WriteDoctorNoteRequest(
                        "a2", 0L, "chief", "present", "past", "allergy", "exam",
                        "diagnosis", "plan", "", "robot", null),
                "d"))
                .isInstanceOf(IllegalArgumentException.class);

        when(repository.findByAppointmentId("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().writeDoctorNote(
                new MedicalRecordController.WriteDoctorNoteRequest(
                        "missing", 0L, "chief", "present", "past", "allergy", "exam",
                        "diagnosis", "plan", "", "HUMAN", null),
                "d"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service().writeDoctorNote(
                new MedicalRecordController.WriteDoctorNoteRequest(
                        "a2", 0L, "chief", "present", "past", "allergy", "exam",
                        "diagnosis", "plan", "", "AI", null),
                "d"))
                .isInstanceOf(IllegalArgumentException.class);

        MedicalRecord saved = service().writeDoctorNote(
                new MedicalRecordController.WriteDoctorNoteRequest(
                        "a2", 0L, "chief", "present", "past", "allergy", "exam",
                        "diagnosis", "plan", "edited", "AI", "ai-1"),
                "d");

        assertThat(saved.getStatus()).isEqualTo(MedicalRecordStatus.ACTIVE);
        verify(auditPublisher).publish(
                "AI_RESULT_CONFIRMED",
                "MEDICAL_RECORD_DIAGNOSIS",
                saved.getId(),
                saved.getPatientId(),
                saved.getAppointmentId(),
                "d",
                "OUTPATIENT_DOCTOR",
                Map.of("aiRecordId", "ai-1", "adoptionStatus", "MODIFIED"));
    }

    @Test
    void archiveRejectsDraftAndWrongDoctorThenPublishesSuccess() {
        MedicalRecord draft = record("draft");
        when(repository.findById("record-draft")).thenReturn(Optional.of(draft));

        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().archive("missing", "d"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service().archive("record-draft", "doctor-x"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> service().archive("record-draft", "d"))
                .isInstanceOf(IllegalStateException.class);

        MedicalRecord active = record("active");
        active.writeDoctorNote("chief", "present", "past", "allergy", "exam", "diagnosis", "plan", "", "HUMAN", null);
        when(repository.findById("record-active")).thenReturn(Optional.of(active));
        when(repository.save(active, 1L)).thenReturn(active);

        MedicalRecord archived = service().archive("record-active", "d");

        assertThat(archived.getStatus()).isEqualTo(MedicalRecordStatus.ARCHIVED);
    }

    @Test
    void savedAccessLogsAndReportLinkDelegateToRepository() {
        MedicalRecord active = record("saved");
        active.writeDoctorNote("chief", "present", "past", "allergy", "exam", "diagnosis", "plan", "", "HUMAN", null);
        when(repository.findByAppointmentId("saved")).thenReturn(Optional.of(active));
        MedicalRecordRepository.AccessLog accessLog =
                new MedicalRecordRepository.AccessLog(1L, "record-1", "p", "d", "OUTPATIENT_DOCTOR", "LIST", "reason",
                        LocalDateTime.parse("2026-07-10T10:00:00"));
        when(repository.accessLogs("p")).thenReturn(java.util.List.of(accessLog));

        assertThat(service().isSaved("saved")).isTrue();
        when(repository.findByAppointmentId("draft")).thenReturn(Optional.of(record("draft")));
        assertThat(service().isSaved("draft")).isFalse();
        assertThat(service().accessLogs("p")).containsExactly(accessLog);
        service().linkReport("saved", "order-1", "report-1", "LAB", "ok", "doctor", LocalDateTime.parse("2026-07-10T11:00:00"));

        verify(repository).linkReport("saved", "order-1", "report-1", "LAB", "ok", "doctor",
                LocalDateTime.parse("2026-07-10T11:00:00"));
    }

    private MedicalRecord record(String appointmentId) {
        return new MedicalRecord(
                "r-" + appointmentId, appointmentId, "p", "patient", "d", "doctor", "dept",
                "2026-06-23", "AM", "summary", "LOW");
    }

    private MedicalRecordService service() {
        return new MedicalRecordService(repository, patientAccessClient, auditPublisher);
    }

    private MedicalRecordController.CreateInitialRecordRequest request() {
        return new MedicalRecordController.CreateInitialRecordRequest(
                "a", "p", "patient", "d", "doctor", "dept", "2026-06-23", "AM", "summary", "LOW");
    }
}
