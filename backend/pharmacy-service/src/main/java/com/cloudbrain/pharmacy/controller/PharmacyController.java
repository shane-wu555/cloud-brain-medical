package com.cloudbrain.pharmacy.controller;

import com.cloudbrain.pharmacy.entity.DrugReturnOrder;
import com.cloudbrain.pharmacy.entity.Prescription;
import com.cloudbrain.pharmacy.repository.PharmacyRepository;
import com.cloudbrain.pharmacy.service.InventoryDemandForecastService;
import com.cloudbrain.pharmacy.service.PharmacyService;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class PharmacyController {
    private final PharmacyService service;
    private final InventoryDemandForecastService inventoryDemandForecastService;
    private final String internalApiKey;

    public PharmacyController(PharmacyService service, InventoryDemandForecastService inventoryDemandForecastService,
                              @Value("${internal.api-key}") String internalApiKey) {
        this.service = service;
        this.inventoryDemandForecastService = inventoryDemandForecastService;
        this.internalApiKey = internalApiKey;
    }

    @GetMapping("/drugs")
    @PreAuthorize("hasAnyRole('OUTPATIENT_DOCTOR','PHARMACY_STAFF','ADMIN')")
    public List<PharmacyRepository.Drug> drugs(@RequestParam(name = "keyword", required = false) String keyword,
                                               @RequestParam(name = "storageCondition", required = false) String storageCondition) {
        return service.drugs(keyword, storageCondition);
    }

    @PostMapping("/drugs/{id}/stock-in")
    @PreAuthorize("hasAnyRole('PHARMACY_STAFF','ADMIN')")
    public PharmacyRepository.Drug stockIn(@PathVariable("id") String id,
                                           @RequestBody StockInRequest request,
                                           JwtAuthenticationToken auth) {
        return service.addStock(id, request, auth.getToken().getSubject());
    }

    @GetMapping("/drugs/inventory-forecast")
    @PreAuthorize("hasAnyRole('PHARMACY_STAFF','ADMIN')")
    public InventoryDemandForecastService.ForecastRun inventoryForecast(
            @RequestParam(name = "lookbackDays", defaultValue = "90") int lookbackDays) {
        return inventoryDemandForecastService.preview(lookbackDays);
    }

    @PostMapping("/prescriptions")
    @PreAuthorize("hasRole('OUTPATIENT_DOCTOR')")
    public Prescription prescribe(@RequestBody CreatePrescriptionRequest request, JwtAuthenticationToken auth) {
        return service.prescribe(request, auth.getToken().getSubject());
    }

    @GetMapping("/prescriptions")
    @PreAuthorize("hasAnyRole('PATIENT','OUTPATIENT_DOCTOR','PHARMACY_STAFF','CASHIER','ADMIN')")
    public List<Prescription> prescriptions(@RequestParam(name = "patientId", required = false) String patientId,
                                            @RequestParam(name = "status", required = false) String status,
                                            JwtAuthenticationToken auth) {
        return service.list(patientId, status, auth.getToken().getSubject(), auth.getToken().getClaimAsString("role"));
    }

    @GetMapping("/prescriptions/{id}")
    @PreAuthorize("hasAnyRole('PATIENT','OUTPATIENT_DOCTOR','PHARMACY_STAFF','CASHIER','ADMIN')")
    public Prescription prescription(@PathVariable("id") String id, JwtAuthenticationToken auth) {
        return service.find(id, auth.getToken().getSubject(), auth.getToken().getClaimAsString("role"));
    }

    @PostMapping("/prescriptions/{id}/dispense")
    @PreAuthorize("hasRole('PHARMACY_STAFF')")
    public Prescription dispense(@PathVariable("id") String id, JwtAuthenticationToken auth) {
        return service.dispense(id, auth.getToken().getSubject());
    }

    @PostMapping("/prescriptions/{id}/return")
    @PreAuthorize("hasRole('PHARMACY_STAFF')")
    public Prescription returnDrugs(@PathVariable("id") String id, @RequestBody ReturnRequest request, JwtAuthenticationToken auth) {
        return service.returnDrugs(id, auth.getToken().getSubject(), request.reason());
    }

    @PostMapping("/prescriptions/{id}/drug-returns")
    @PreAuthorize("hasRole('OUTPATIENT_DOCTOR')")
    public DrugReturnOrder createDrugReturn(@PathVariable("id") String id,
                                            @RequestBody CreateDrugReturnRequest request,
                                            JwtAuthenticationToken auth) {
        return service.createDrugReturn(id, request, auth.getToken().getSubject());
    }

    @GetMapping("/drug-returns")
    @PreAuthorize("hasAnyRole('PATIENT','OUTPATIENT_DOCTOR','PHARMACY_STAFF','CASHIER','ADMIN')")
    public List<DrugReturnOrder> drugReturns(@RequestParam(name = "patientId", required = false) String patientId,
                                             @RequestParam(name = "status", required = false) String status,
                                             JwtAuthenticationToken auth) {
        return service.drugReturns(patientId, status, auth.getToken().getSubject(), auth.getToken().getClaimAsString("role"));
    }

    @PostMapping("/internal/prescriptions/{id}/payment-confirmation")
    public Prescription paymentConfirmation(@PathVariable("id") String id,
                                            @RequestBody PaymentConfirmation request,
                                            @RequestHeader(name = "X-Internal-Api-Key", required = false) String key) {
        checkKey(key);
        return service.confirmPayment(id, request.patientId(), request.paymentOrderId());
    }

    @PostMapping("/internal/drug-returns/{id}/refund-completion")
    public DrugReturnOrder refundCompletion(@PathVariable("id") String id,
                                            @RequestBody RefundCompletion request,
                                            @RequestHeader(name = "X-Internal-Api-Key", required = false) String key) {
        checkKey(key);
        return service.completeDrugReturn(id, request.cashierId(), request.refundOrderId());
    }

    private void checkKey(String key) {
        if (!internalApiKey.equals(key)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "内部接口认证失败");
    }

    public record CreatePrescriptionRequest(
            String appointmentId,
            String medicalRecordId,
            String patientId,
            String patientName,
            String diagnosis,
            String aiAssistanceId,
            String aiAdoptionStatus,
            String aiRevisionNote,
            List<PrescriptionItemRequest> items) {
    }

    public record PrescriptionItemRequest(
            String drugId,
            int quantity,
            String dosage,
            String usage,
            String frequency,
            int days,
            String note) {
    }

    public record ReturnRequest(String reason) {
    }

    public record StockInRequest(int quantity, String reason) {
    }

    public record CreateDrugReturnRequest(String doctorOpinion, String opinionTemplate) {
    }

    public record PaymentConfirmation(String patientId, String paymentOrderId) {
    }

    public record RefundCompletion(String cashierId, String refundOrderId) {
    }
}
