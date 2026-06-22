package com.cloudbrain.medicalrecord.controller;

import com.cloudbrain.medicalrecord.entity.MedicalRecord;
import com.cloudbrain.medicalrecord.repository.MedicalRecordRepository;
import com.cloudbrain.medicalrecord.service.MedicalRecordService;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/medical-records")
public class MedicalRecordController {
    private final MedicalRecordService service; private final String internalApiKey;
    public MedicalRecordController(MedicalRecordService service,@Value("${internal.api-key}") String key){this.service=service;this.internalApiKey=key;}

    @GetMapping @PreAuthorize("hasAnyRole('PATIENT','OUTPATIENT_DOCTOR')")
    public List<MedicalRecord> list(@RequestParam(required=false) String patientId,@RequestParam(required=false) String appointmentId,
            @RequestParam(required=false) String status,JwtAuthenticationToken auth){return service.listAuthorized(auth.getToken().getSubject(),auth.getToken().getClaimAsString("role"),patientId,appointmentId,status);}

    @GetMapping("/history") @PreAuthorize("hasRole('OUTPATIENT_DOCTOR')")
    public List<MedicalRecord> history(@RequestParam String patientId,@RequestParam String currentAppointmentId,
            @RequestParam String reason,JwtAuthenticationToken auth){return service.history(patientId,currentAppointmentId,reason,auth.getToken().getSubject());}

    @GetMapping("/access-logs") @PreAuthorize("hasRole('ADMIN')")
    public List<MedicalRecordRepository.AccessLog> accessLogs(@RequestParam(required=false) String patientId){return service.accessLogs(patientId);}

    @PostMapping("/initial") public MedicalRecord createInitial(@RequestBody CreateInitialRecordRequest request,
            @RequestHeader(name="X-Internal-Api-Key",required=false) String key){checkKey(key);return service.createInitial(request);}

    @GetMapping("/internal/{appointmentId}/saved") public Map<String,Boolean> saved(@PathVariable String appointmentId,
            @RequestHeader(name="X-Internal-Api-Key",required=false) String key){checkKey(key);return Map.of("saved",service.isSaved(appointmentId));}
    @PostMapping("/internal/{appointmentId}/reports") public void linkReport(@PathVariable String appointmentId,@RequestHeader(name="X-Internal-Api-Key",required=false)String key,@RequestBody ReportLink request){checkKey(key);service.linkReport(appointmentId,request.medicalOrderId(),request.reportId(),request.reportType(),request.conclusion(),request.confirmedBy(),request.confirmedAt());}

    @PostMapping("/doctor-note") @PreAuthorize("hasRole('OUTPATIENT_DOCTOR')")
    public MedicalRecord writeDoctorNote(@RequestBody WriteDoctorNoteRequest request,JwtAuthenticationToken auth){return service.writeDoctorNote(request,auth.getToken().getSubject());}

    @PostMapping("/{id}/archive") @PreAuthorize("hasRole('OUTPATIENT_DOCTOR')")
    public MedicalRecord archive(@PathVariable String id,JwtAuthenticationToken auth){return service.archive(id,auth.getToken().getSubject());}

    private void checkKey(String key){if(!internalApiKey.equals(key))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"内部接口认证失败");}
    public record CreateInitialRecordRequest(String appointmentId,String patientId,String patientName,String doctorId,String doctorName,
            String departmentName,String visitDate,String period,String triageSummary,String riskLevel){}
    public record WriteDoctorNoteRequest(String appointmentId,long version,String chiefComplaint,String presentIllness,String pastHistory,
            String allergyHistory,String physicalExamination,String preliminaryDiagnosis,String treatmentPlan,String doctorRevisionNote,
            String diagnosisCreatedByType,String diagnosisAiRecordId){}
    public record ReportLink(String medicalOrderId,String reportId,String reportType,String conclusion,String confirmedBy,java.time.LocalDateTime confirmedAt){}
}
