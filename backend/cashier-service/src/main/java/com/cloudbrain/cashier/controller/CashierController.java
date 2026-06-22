package com.cloudbrain.cashier.controller;

import com.cloudbrain.cashier.repository.CashierRepository;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CashierController {
    private final CashierRepository repository;
    public CashierController(CashierRepository repository) { this.repository=repository; }
    @GetMapping("/payments") @PreAuthorize("hasAnyRole('PATIENT','CASHIER','ADMIN')")
    public List<CashierRepository.Payment> payments(@RequestParam(required=false) String patientId,@RequestParam(required=false) String businessId,JwtAuthenticationToken auth) {
        return repository.payments(restrict(patientId,auth),businessId);
    }
    @GetMapping("/refunds") @PreAuthorize("hasAnyRole('PATIENT','CASHIER','ADMIN')")
    public List<CashierRepository.Refund> refunds(@RequestParam(required=false) String patientId,@RequestParam(required=false) String businessId,JwtAuthenticationToken auth) {
        return repository.refunds(restrict(patientId,auth),businessId);
    }
    private String restrict(String patientId,JwtAuthenticationToken auth) {
        if(!"PATIENT".equals(auth.getToken().getClaimAsString("role"))) return patientId;
        String subject=auth.getToken().getSubject(); if(patientId!=null&&!subject.equals(patientId)) throw new AccessDeniedException("患者只能查看自己的费用记录"); return subject;
    }
}
