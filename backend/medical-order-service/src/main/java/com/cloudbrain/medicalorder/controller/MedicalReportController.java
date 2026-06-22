package com.cloudbrain.medicalorder.controller;

import com.cloudbrain.medicalorder.domain.*;
import com.cloudbrain.medicalorder.service.MedicalReportService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/medical-orders")
public class MedicalReportController {
    private final MedicalReportService service;public MedicalReportController(MedicalReportService service){this.service=service;}
    @PostMapping(value="/{orderId}/attachments",consumes="multipart/form-data") @PreAuthorize("hasRole('CHECK_DOCTOR')")
    public MedicalAttachment upload(@PathVariable String orderId,@RequestPart("file")MultipartFile file,JwtAuthenticationToken auth){return service.upload(orderId,file,auth.getToken().getSubject());}
    @GetMapping("/{orderId}/attachments") @PreAuthorize("isAuthenticated()")
    public List<MedicalAttachment> attachments(@PathVariable String orderId,JwtAuthenticationToken auth){return service.attachments(orderId,auth.getToken().getSubject(),auth.getToken().getClaimAsString("role"));}
    @PostMapping("/{orderId}/ct-analysis") @PreAuthorize("hasRole('CHECK_DOCTOR')")
    public AiMedicalTask analyze(@PathVariable String orderId,@RequestBody CtRequest request,JwtAuthenticationToken auth){return service.submitCt(orderId,request.attachmentId(),auth.getToken().getSubject());}
    @GetMapping("/ai-tasks/{taskId}") @PreAuthorize("hasRole('CHECK_DOCTOR')")
    public AiMedicalTask task(@PathVariable String taskId,JwtAuthenticationToken auth){return service.refresh(taskId,auth.getToken().getSubject());}
    @PostMapping("/{orderId}/reports/draft") @PreAuthorize("hasAnyRole('CHECK_DOCTOR','LAB_DOCTOR','DISPOSAL_DOCTOR')")
    public MedicalReport draft(@PathVariable String orderId,@RequestBody ReportRequest request,JwtAuthenticationToken auth){return service.manualDraft(orderId,request.findings(),request.conclusion(),request.advice(),auth.getToken().getSubject());}
    @PostMapping("/{orderId}/reports/confirm") @PreAuthorize("hasAnyRole('CHECK_DOCTOR','LAB_DOCTOR','DISPOSAL_DOCTOR')")
    public MedicalReport confirm(@PathVariable String orderId,@RequestBody ReportRequest request,JwtAuthenticationToken auth){return service.confirm(orderId,request.findings(),request.conclusion(),request.advice(),auth.getToken().getSubject(),auth.getToken().getClaimAsString("role"));}
    @PostMapping("/{orderId}/reports/reject") @PreAuthorize("hasAnyRole('CHECK_DOCTOR','LAB_DOCTOR','DISPOSAL_DOCTOR')")
    public MedicalReport reject(@PathVariable String orderId,@RequestBody RejectRequest request,JwtAuthenticationToken auth){return service.reject(orderId,request.reason(),auth.getToken().getSubject(),auth.getToken().getClaimAsString("role"));}
    @GetMapping("/reports") @PreAuthorize("hasAnyRole('PATIENT','OUTPATIENT_DOCTOR','CHECK_DOCTOR','LAB_DOCTOR','DISPOSAL_DOCTOR')")
    public List<MedicalReport> reports(@RequestParam(required=false)String patientId,JwtAuthenticationToken auth){return service.list(patientId,auth.getToken().getSubject(),auth.getToken().getClaimAsString("role"));}
    public record CtRequest(String attachmentId){}public record ReportRequest(String findings,String conclusion,String advice){}public record RejectRequest(String reason){}
}
