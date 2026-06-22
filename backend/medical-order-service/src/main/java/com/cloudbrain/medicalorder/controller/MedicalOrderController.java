package com.cloudbrain.medicalorder.controller;

import com.cloudbrain.medicalorder.domain.MedicalOrder;
import com.cloudbrain.medicalorder.service.MedicalOrderService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/medical-orders")
public class MedicalOrderController {
    private final MedicalOrderService service;
    public MedicalOrderController(MedicalOrderService service) { this.service = service; }

    @PostMapping
    @PreAuthorize("hasRole('OUTPATIENT_DOCTOR')")
    public MedicalOrder create(@RequestBody CreateRequest request, JwtAuthenticationToken authentication) {
        return service.create(request, authentication.getToken().getSubject());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PATIENT','OUTPATIENT_DOCTOR','CHECK_DOCTOR','LAB_DOCTOR','DISPOSAL_DOCTOR','ADMIN')")
    public List<MedicalOrder> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String patientId,
            JwtAuthenticationToken authentication) {
        if ("PATIENT".equals(authentication.getToken().getClaimAsString("role"))) {
            if (patientId != null && !patientId.equals(authentication.getToken().getSubject())) {
                throw new org.springframework.security.access.AccessDeniedException("患者只能查看自己的医技申请");
            }
            patientId = authentication.getToken().getSubject();
        }
        return service.listAuthorized(type,status,patientId,authentication.getToken().getSubject(),authentication.getToken().getClaimAsString("role"));
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasRole('CASHIER')")
    public MedicalOrder pay(@PathVariable String id, JwtAuthenticationToken authentication) {
        return service.pay(id, authentication.getToken().getSubject(), authentication.getToken().getClaimAsString("role"));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('CHECK_DOCTOR','LAB_DOCTOR','DISPOSAL_DOCTOR')")
    public MedicalOrder start(@PathVariable String id, JwtAuthenticationToken authentication) {
        return service.start(id, authentication.getToken().getSubject(), authentication.getToken().getClaimAsString("role"));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('CHECK_DOCTOR','LAB_DOCTOR','DISPOSAL_DOCTOR')")
    public MedicalOrder complete(@PathVariable String id, @RequestBody CompleteRequest request, JwtAuthenticationToken authentication) {
        return service.complete(id, authentication.getToken().getSubject(), authentication.getToken().getClaimAsString("role"),
                request.resultData(), request.summary(), request.createdByType(), request.aiRecordId());
    }
    @PostMapping("/{id}/miss") @PreAuthorize("hasAnyRole('CHECK_DOCTOR','LAB_DOCTOR','DISPOSAL_DOCTOR')")
    public MedicalOrder miss(@PathVariable String id,JwtAuthenticationToken authentication){return service.miss(id,authentication.getToken().getSubject(),authentication.getToken().getClaimAsString("role"));}

    public record CreateRequest(
            String appointmentId, String patientId, String patientName, String orderType,
            String projectCode, String projectName, String purpose, String bodyPart, BigDecimal amount,String urgency) {}
    public record CompleteRequest(String resultData, String summary, String createdByType, String aiRecordId) {}
}
