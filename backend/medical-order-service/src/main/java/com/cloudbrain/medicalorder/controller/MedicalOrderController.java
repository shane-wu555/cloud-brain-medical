package com.cloudbrain.medicalorder.controller;

import com.cloudbrain.medicalorder.domain.MedicalOrder;
import com.cloudbrain.medicalorder.service.MedicalOrderService;
import com.cloudbrain.medicalorder.service.PatientAccessClient;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
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
    private final PatientAccessClient patientAccessClient;

    public MedicalOrderController(MedicalOrderService service, PatientAccessClient patientAccessClient) {
        this.service = service;
        this.patientAccessClient = patientAccessClient;
    }

    @PostMapping
    @PreAuthorize("hasRole('OUTPATIENT_DOCTOR')")
    public MedicalOrder create(@RequestBody CreateRequest request, JwtAuthenticationToken authentication) {
        return service.create(request, authentication.getToken().getSubject());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PATIENT','OUTPATIENT_DOCTOR','CHECK_DOCTOR','LAB_DOCTOR','DISPOSAL_DOCTOR','CASHIER','ADMIN')")
    public List<MedicalOrder> list(
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "patientId", required = false) String patientId,
            @RequestParam(name = "appointmentId", required = false) String appointmentId,
            @RequestParam(name = "view", required = false) String view,
            JwtAuthenticationToken authentication) {
        if ("PATIENT".equals(authentication.getToken().getClaimAsString("role"))) {
            String accountId = authentication.getToken().getSubject();
            if (patientId == null || patientId.isBlank()) {
                patientId = patientAccessClient.boundPatientId(accountId);
                if (patientId == null || patientId.isBlank()) throw new AccessDeniedException("请先添加并绑定就诊人");
            }
            if (!patientAccessClient.owns(accountId, patientId)) {
                throw new AccessDeniedException("患者只能查看自己账号名下就诊人的医技申请");
            }
        }
        return service.listAuthorized(type, status, patientId, appointmentId, view, authentication.getToken().getSubject(),
                authentication.getToken().getClaimAsString("role"));
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasRole('CASHIER')")
    public MedicalOrder pay(@PathVariable("id") String id, JwtAuthenticationToken authentication) {
        return service.pay(id, authentication.getToken().getSubject(), authentication.getToken().getClaimAsString("role"));
    }

    @PostMapping("/{id}/call")
    @PreAuthorize("hasAnyRole('CHECK_DOCTOR','LAB_DOCTOR','DISPOSAL_DOCTOR')")
    public MedicalOrder call(@PathVariable("id") String id, JwtAuthenticationToken authentication) {
        return service.call(id, authentication.getToken().getSubject(), authentication.getToken().getClaimAsString("role"));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('CHECK_DOCTOR','LAB_DOCTOR','DISPOSAL_DOCTOR')")
    public MedicalOrder start(@PathVariable("id") String id, JwtAuthenticationToken authentication) {
        return service.start(id, authentication.getToken().getSubject(), authentication.getToken().getClaimAsString("role"));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('CHECK_DOCTOR','LAB_DOCTOR','DISPOSAL_DOCTOR')")
    public MedicalOrder complete(@PathVariable("id") String id, @RequestBody CompleteRequest request,
            JwtAuthenticationToken authentication) {
        return service.complete(id, authentication.getToken().getSubject(), authentication.getToken().getClaimAsString("role"),
                request.summary(), request.createdByType(), request.aiRecordId());
    }

    @PostMapping("/{id}/miss")
    @PreAuthorize("hasAnyRole('CHECK_DOCTOR','LAB_DOCTOR','DISPOSAL_DOCTOR')")
    public MedicalOrder miss(@PathVariable("id") String id, JwtAuthenticationToken authentication) {
        return service.miss(id, authentication.getToken().getSubject(), authentication.getToken().getClaimAsString("role"));
    }

    public record CreateRequest(
            String appointmentId, String patientId, String patientName, String orderType,
            String itemCode, String itemName, String purpose, String bodyPart, BigDecimal amount, String urgency) {
    }

    public record CompleteRequest(String summary, String createdByType, String aiRecordId) {
    }
}
