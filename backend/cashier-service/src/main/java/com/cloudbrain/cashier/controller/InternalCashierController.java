package com.cloudbrain.cashier.controller;

import com.cloudbrain.cashier.repository.CashierRepository;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/internal")
public class InternalCashierController {
    private final CashierRepository repository; private final String key;
    public InternalCashierController(CashierRepository repository, @Value("${internal.api-key}") String key) { this.repository=repository; this.key=key; }
    @PostMapping("/payments") public void payment(@RequestHeader(name="X-Internal-Api-Key",required=false) String candidate,@RequestBody PaymentCommand c) {
        check(candidate); repository.recordPayment(c.businessType(),c.businessId(),c.patientId(),value(c.amount()),c.paymentMethod(),c.operatorId());
    }
    @PostMapping("/refunds") public void refund(@RequestHeader(name="X-Internal-Api-Key",required=false) String candidate,@RequestBody RefundCommand c) {
        check(candidate); repository.recordRefund(c.businessType(),c.businessId(),c.patientId(),value(c.amount()),c.reason(),c.operatorId());
    }
    private void check(String candidate) { if(!key.equals(candidate)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"内部接口认证失败"); }
    private BigDecimal value(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    public record PaymentCommand(String businessType,String businessId,String patientId,BigDecimal amount,String paymentMethod,String operatorId) {}
    public record RefundCommand(String businessType,String businessId,String patientId,BigDecimal amount,String reason,String operatorId) {}
}
