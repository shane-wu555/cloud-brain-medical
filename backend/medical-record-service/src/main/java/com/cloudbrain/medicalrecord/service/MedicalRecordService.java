package com.cloudbrain.medicalrecord.service;

import com.cloudbrain.medicalrecord.controller.MedicalRecordController;
import com.cloudbrain.medicalrecord.entity.MedicalRecord;
import com.cloudbrain.medicalrecord.repository.MedicalRecordRepository;
import java.util.List;
import java.util.Optional;
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
                        "record-" + String.format("%03d", repository.size() + 1),
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
        record.writeDoctorNote(
                request.chiefComplaint(),
                request.presentIllness(),
                request.diagnosis(),
                request.treatmentPlan(),
                request.doctorRevisionNote());
        return repository.save(record);
    }

    public MedicalRecord archive(String id) {
        MedicalRecord record = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("病历不存在"));
        record.archive();
        return repository.save(record);
    }
}

