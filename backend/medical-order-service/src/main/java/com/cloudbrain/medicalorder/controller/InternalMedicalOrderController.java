package com.cloudbrain.medicalorder.controller;

import com.cloudbrain.medicalorder.domain.MedicalOrder;
import com.cloudbrain.medicalorder.service.MedicalOrderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/internal/medical-orders")
public class InternalMedicalOrderController {
    private final MedicalOrderService service;private final String key;
    public InternalMedicalOrderController(MedicalOrderService service,@Value("${internal.api-key}") String key){this.service=service;this.key=key;}
    @PostMapping("/{id}/payment-confirmation") public MedicalOrder confirm(@PathVariable("id") String id,@RequestHeader(name="X-Internal-Api-Key",required=false)String candidate,@RequestBody Command command){if(!key.equals(candidate))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return service.pay(id,command.patientId(),"PATIENT");}
    public record Command(String patientId,String paymentOrderId){}
}
