package com.cloudbrain.medicalorder.controller;

import com.cloudbrain.medicalorder.domain.LaboratoryResultItem;
import com.cloudbrain.medicalorder.domain.Specimen;
import com.cloudbrain.medicalorder.service.LaboratoryWorkflowService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/medical-orders")
public class LaboratoryWorkflowController {
    private final LaboratoryWorkflowService service;
    public LaboratoryWorkflowController(LaboratoryWorkflowService service) { this.service = service; }

    @PostMapping("/{orderId}/specimens")
    @PreAuthorize("hasRole('LAB_DOCTOR')")
    public Specimen create(@PathVariable String orderId, @RequestBody CreateSpecimenRequest request) {
        return service.create(orderId, request.specimenType(), request.barcode());
    }

    @GetMapping("/{orderId}/specimens")
    @PreAuthorize("hasAnyRole('LAB_DOCTOR','OUTPATIENT_DOCTOR','ADMIN')")
    public List<Specimen> specimens(@PathVariable String orderId) { return service.specimens(orderId); }

    @PostMapping("/specimens/{specimenId}/status")
    @PreAuthorize("hasRole('LAB_DOCTOR')")
    public Specimen transition(@PathVariable String specimenId, @RequestBody StatusRequest request,
                               JwtAuthenticationToken authentication) {
        return service.transition(specimenId, request.status(), authentication.getToken().getSubject(), request.reason());
    }

    @PostMapping("/{orderId}/laboratory-results")
    @PreAuthorize("hasRole('LAB_DOCTOR')")
    public List<LaboratoryResultItem> saveResults(@PathVariable String orderId, @RequestBody SaveResultsRequest request,
                                                  JwtAuthenticationToken authentication) {
        return service.saveResults(orderId, request.specimenId(), request.items(), authentication.getToken().getSubject());
    }

    @GetMapping("/{orderId}/laboratory-results")
    @PreAuthorize("hasAnyRole('LAB_DOCTOR','OUTPATIENT_DOCTOR','ADMIN')")
    public List<LaboratoryResultItem> results(@PathVariable String orderId) { return service.results(orderId); }

    public record CreateSpecimenRequest(String specimenType, String barcode) {}
    public record StatusRequest(String status, String reason) {}
    public record SaveResultsRequest(String specimenId, List<ResultItemRequest> items) {}
    public record ResultItemRequest(String itemCode, String itemName, String resultValue, String unit,
                                    String referenceRange, String abnormalFlag, String createdByType, String aiRecordId) {}
}
