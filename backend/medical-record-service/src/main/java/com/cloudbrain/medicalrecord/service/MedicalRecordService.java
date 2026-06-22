package com.cloudbrain.medicalrecord.service;

import com.cloudbrain.medicalrecord.controller.MedicalRecordController;
import com.cloudbrain.medicalrecord.entity.MedicalRecord;
import com.cloudbrain.medicalrecord.repository.MedicalRecordRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MedicalRecordService {
    private final MedicalRecordRepository repository;

    public MedicalRecordService(MedicalRecordRepository repository) {
        this.repository = repository;
    }

    public List<MedicalRecord> list(String patientId, String appointmentId, String status) {
        return repository.findAll().stream()
                .filter(item -> Optional.ofNullable(patientId).map(id -> id.equals(item.getPatientId())).orElse(true))
                .filter(item -> Optional.ofNullable(appointmentId).map(id -> id.equals(item.getAppointmentId())).orElse(true))
                .filter(item -> Optional.ofNullable(status).map(value -> value.equals(item.getStatus().name())).orElse(true))
                .toList();
    }

    public MedicalRecord createInitial(MedicalRecordController.CreateInitialRecordRequest request) {
        return repository.findByAppointmentId(request.appointmentId())
                .orElseGet(() -> repository.save(new MedicalRecord(
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

    public MedicalRecord writeDoctorNote(MedicalRecordController.WriteDoctorNoteRequest request) {
        MedicalRecord record = repository.findByAppointmentId(request.appointmentId())
                .orElseThrow(() -> new IllegalArgumentException("本次就诊病历不存在"));
        String source = request.diagnosisCreatedByType() == null ? "HUMAN" : request.diagnosisCreatedByType().toUpperCase();
        if (!source.equals("HUMAN") && !source.equals("AI")) throw new IllegalArgumentException("diagnosisCreatedByType 必须为 HUMAN 或 AI");
        if (source.equals("AI") && (request.diagnosisAiRecordId() == null || request.diagnosisAiRecordId().isBlank())) {
            throw new IllegalArgumentException("AI 生成诊断必须关联 diagnosisAiRecordId");
        }
        record.writeDoctorNote(
                request.chiefComplaint(),
                request.presentIllness(),
                request.diagnosis(),
                request.treatmentPlan(),
                request.doctorRevisionNote(),
                source,
                request.diagnosisAiRecordId());
        return repository.save(record);
    }

    public MedicalRecord archive(String id) {
        MedicalRecord record = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("病历不存在"));
        record.archive();
        return repository.save(record);
    }
}
