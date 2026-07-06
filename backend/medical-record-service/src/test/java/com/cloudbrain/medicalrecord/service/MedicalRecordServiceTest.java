package com.cloudbrain.medicalrecord.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.medicalrecord.audit.AuditPublisher;
import com.cloudbrain.medicalrecord.controller.MedicalRecordController;
import com.cloudbrain.medicalrecord.entity.MedicalRecord;
import com.cloudbrain.medicalrecord.repository.MedicalRecordRepository;
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
