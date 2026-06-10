package com.cloudbrain.medicalrecord.controller;

import com.cloudbrain.medicalrecord.entity.MedicalRecord;
import com.cloudbrain.medicalrecord.service.MedicalRecordService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/medical-records")
public class MedicalRecordController {
    private final MedicalRecordService service;

    public MedicalRecordController(MedicalRecordService service) {
        this.service = service;
    }

    @GetMapping
    public List<MedicalRecord> list(
            @RequestParam(name = "patientId", required = false) String patientId,
            @RequestParam(name = "appointmentId", required = false) String appointmentId,
            @RequestParam(name = "status", required = false) String status) {
        return service.list(patientId, appointmentId, status);
    }

    @PostMapping("/initial")
    public MedicalRecord createInitial(@RequestBody CreateInitialRecordRequest request) {
        return service.createInitial(request);
    }

    @PostMapping("/doctor-note")
    public MedicalRecord writeDoctorNote(@RequestBody WriteDoctorNoteRequest request) {
        return service.writeDoctorNote(request);
    }

    @PostMapping("/{id}/archive")
    public MedicalRecord archive(@PathVariable("id") String id) {
        return service.archive(id);
    }

    public record CreateInitialRecordRequest(
            String appointmentId,
            String patientId,
            String patientName,
            String doctorId,
            String doctorName,
            String departmentName,
            String visitDate,
            String period,
            String triageSummary,
            String riskLevel) {
    }

    public record WriteDoctorNoteRequest(
            String appointmentId,
            String chiefComplaint,
            String presentIllness,
            String diagnosis,
            String treatmentPlan,
            String doctorRevisionNote) {
    }
}

