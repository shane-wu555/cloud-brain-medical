package com.cloudbrain.medicalrecord.service;

import com.cloudbrain.medicalrecord.audit.AuditPublisher;
import com.cloudbrain.medicalrecord.controller.MedicalRecordController;
import com.cloudbrain.medicalrecord.entity.MedicalRecord;
import com.cloudbrain.medicalrecord.entity.MedicalRecordStatus;
import com.cloudbrain.medicalrecord.repository.MedicalRecordRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MedicalRecordService {
    private final MedicalRecordRepository repository;
    private final PatientAccessClient patientAccessClient;
    private final AuditPublisher auditPublisher;

    public MedicalRecordService(
            MedicalRecordRepository repository,
            PatientAccessClient patientAccessClient,
            AuditPublisher auditPublisher) {
        this.repository = repository;
        this.patientAccessClient = patientAccessClient;
        this.auditPublisher = auditPublisher;
    }

    public List<MedicalRecord> listAuthorized(
            String actorId,
            String role,
            String patientId,
            String appointmentId,
            String status) {
        List<MedicalRecord> records;
        if ("PATIENT".equals(role)) {
            String scopedPatientId = patientId;
            if (blank(scopedPatientId)) {
                scopedPatientId = patientAccessClient.boundPatientId(actorId);
            }
            if (blank(scopedPatientId)) {
                throw new AccessDeniedException("Patient account is not bound to a patient profile");
            }
            if (!patientAccessClient.owns(actorId, scopedPatientId)) {
                throw new AccessDeniedException("Cannot access another patient's medical records");
            }
            records = repository.findByPatientIdExcludingCancelledAppointments(scopedPatientId);
            patientId = scopedPatientId;
        } else if ("OUTPATIENT_DOCTOR".equals(role)) {
            records = appointmentId != null
                    ? repository.findByAppointmentId(appointmentId).stream().toList()
                    : repository.findAllExcludingCancelledAppointments().stream()
                            .filter(record -> record.getDoctorId().equals(actorId))
                            .toList();
        } else {
            throw new AccessDeniedException("Role cannot access medical records");
        }

        String finalPatientId = patientId;
        List<MedicalRecord> visible = records.stream()
                .filter(record -> finalPatientId == null || record.getPatientId().equals(finalPatientId))
                .filter(record -> appointmentId == null || record.getAppointmentId().equals(appointmentId))
                .filter(record -> status == null || record.getStatus().name().equals(status))
                .peek(record -> {
                    if ("OUTPATIENT_DOCTOR".equals(role) && !record.getDoctorId().equals(actorId)) {
                        throw new AccessDeniedException("Doctor can only access owned medical records");
                    }
                })
                .toList();

        auditPublisher.publish(
                appointmentId == null ? "MEDICAL_RECORD_LIST_VIEW" : "MEDICAL_RECORD_DETAIL_VIEW",
                "MEDICAL_RECORD",
                visible.size() == 1 ? visible.get(0).getId() : null,
                finalPatientId,
                appointmentId,
                actorId,
                role,
                Map.of(
                        "accessScope", appointmentId == null ? "LIST" : "APPOINTMENT",
                        "statusFilter", blank(status) ? "ALL" : status.trim(),
                        "resultCount", visible.size()));
        return visible;
    }

    public List<MedicalRecord> history(
            String patientId,
            String currentAppointmentId,
            String reason,
            String doctorId) {
        if (blank(reason)) {
            throw new IllegalArgumentException("Viewing historical records requires a reason");
        }
        MedicalRecord current = repository.findByAppointmentId(currentAppointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Current medical record does not exist"));
        if (!current.getDoctorId().equals(doctorId) || !current.getPatientId().equals(patientId)) {
            throw new AccessDeniedException("Doctor cannot access another patient's history");
        }

        List<MedicalRecord> history = repository.findByPatientIdExcludingCancelledAppointments(patientId).stream()
                .filter(record -> !record.getAppointmentId().equals(currentAppointmentId))
                .toList();
        history.forEach(record ->
                repository.recordAccess(record.getId(), patientId, doctorId, "OUTPATIENT_DOCTOR", "HISTORY", reason));
        auditPublisher.publish(
                "MEDICAL_RECORD_HISTORY_VIEW",
                "MEDICAL_RECORD",
                null,
                patientId,
                currentAppointmentId,
                doctorId,
                "OUTPATIENT_DOCTOR",
                Map.of(
                        "accessScope", "HISTORY",
                        "reason", reason,
                        "resultCount", history.size()));
        return history;
    }

    public MedicalRecord createInitial(MedicalRecordController.CreateInitialRecordRequest request) {
        return repository.findByAppointmentId(request.appointmentId()).orElseGet(() ->
                repository.createInitialIfAbsent(new MedicalRecord(
                        "record-" + UUID.randomUUID(),
                        request.appointmentId(),
                        request.patientId(),
                        request.patientName(),
                        request.doctorId(),
                        request.doctorName(),
                        request.departmentName(),
                        request.visitDate(),
                        request.period(),
                        request.triageSummary(),
                        request.riskLevel())));
    }

    @Transactional
    public MedicalRecord writeDoctorNote(MedicalRecordController.WriteDoctorNoteRequest request, String doctorId) {
        MedicalRecord record = repository.findByAppointmentId(request.appointmentId())
                .orElseThrow(() -> new IllegalArgumentException("Medical record does not exist"));
        if (!record.getDoctorId().equals(doctorId)) {
            throw new AccessDeniedException("Doctor can only edit owned medical records");
        }
        if (record.getVersion() != request.version()) {
            throw new OptimisticLockingFailureException("Medical record was updated by another request");
        }

        String source = request.diagnosisCreatedByType() == null
                ? "HUMAN"
                : request.diagnosisCreatedByType().toUpperCase();
        if (!List.of("HUMAN", "AI").contains(source)) {
            throw new IllegalArgumentException("diagnosisCreatedByType must be HUMAN or AI");
        }
        if ("AI".equals(source) && blank(request.diagnosisAiRecordId())) {
            throw new IllegalArgumentException("AI-generated diagnosis must include diagnosisAiRecordId");
        }

        long expectedVersion = record.getVersion();
        record.writeDoctorNote(
                request.chiefComplaint(),
                request.presentIllness(),
                request.pastHistory(),
                request.allergyHistory(),
                request.physicalExamination(),
                request.preliminaryDiagnosis(),
                request.treatmentPlan(),
                request.doctorRevisionNote(),
                source,
                request.diagnosisAiRecordId());
        MedicalRecord saved = repository.save(record, expectedVersion);
        auditPublisher.publish(
                "MEDICAL_RECORD_UPDATE",
                "MEDICAL_RECORD",
                saved.getId(),
                saved.getPatientId(),
                saved.getAppointmentId(),
                doctorId,
                "OUTPATIENT_DOCTOR",
                Map.of(
                        "diagnosisSource", source,
                        "status", saved.getStatus().name(),
                        "version", saved.getVersion()));
        if ("AI".equals(source)) {
            auditPublisher.publish(
                    "AI_RESULT_CONFIRMED",
                    "MEDICAL_RECORD_DIAGNOSIS",
                    saved.getId(),
                    saved.getPatientId(),
                    saved.getAppointmentId(),
                    doctorId,
                    "OUTPATIENT_DOCTOR",
                    Map.of(
                            "aiRecordId", request.diagnosisAiRecordId(),
                            "adoptionStatus", blank(request.doctorRevisionNote()) ? "ADOPTED" : "MODIFIED"));
        }
        return saved;
    }

    @Transactional
    public MedicalRecord archive(String id, String doctorId) {
        MedicalRecord record = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Medical record does not exist"));
        if (!record.getDoctorId().equals(doctorId)) {
            throw new AccessDeniedException("Doctor cannot archive another doctor's record");
        }
        if (record.getStatus() == MedicalRecordStatus.DRAFT) {
            throw new IllegalStateException("Draft medical record cannot be archived");
        }

        long expectedVersion = record.getVersion();
        record.archive();
        MedicalRecord saved = repository.save(record, expectedVersion);
        auditPublisher.publish(
                "MEDICAL_RECORD_ARCHIVE",
                "MEDICAL_RECORD",
                saved.getId(),
                saved.getPatientId(),
                saved.getAppointmentId(),
                doctorId,
                "OUTPATIENT_DOCTOR",
                Map.of("status", saved.getStatus().name(), "version", saved.getVersion()));
        return saved;
    }

    public boolean isSaved(String appointmentId) {
        return repository.findByAppointmentId(appointmentId)
                .map(record -> record.getStatus() != MedicalRecordStatus.DRAFT)
                .orElse(false);
    }

    public List<MedicalRecordRepository.AccessLog> accessLogs(String patientId) {
        return repository.accessLogs(patientId);
    }

    public void linkReport(
            String appointmentId,
            String orderId,
            String reportId,
            String type,
            String conclusion,
            String confirmer,
            LocalDateTime confirmedAt) {
        repository.linkReport(appointmentId, orderId, reportId, type, conclusion, confirmer, confirmedAt);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
