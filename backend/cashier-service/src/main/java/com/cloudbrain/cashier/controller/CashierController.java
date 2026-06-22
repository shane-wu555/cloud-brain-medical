package com.cloudbrain.cashier.controller;

import com.cloudbrain.cashier.repository.CashierRepository;
import com.cloudbrain.cashier.service.AppointmentPaymentClient;
import com.cloudbrain.cashier.service.MedicalOrderPaymentClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CashierController {
    private final CashierRepository repository;
    private final boolean testModeEnabled;
    private final AppointmentPaymentClient appointmentClient;
    private final MedicalOrderPaymentClient medicalOrderClient;
    public CashierController(CashierRepository repository,
                             AppointmentPaymentClient appointmentClient,
                             MedicalOrderPaymentClient medicalOrderClient,
                             @Value("${payment.test-mode-enabled:false}") boolean testModeEnabled) {
        this.repository=repository;
        this.appointmentClient=appointmentClient;
        this.medicalOrderClient=medicalOrderClient;
        this.testModeEnabled=testModeEnabled;
    }
    @PostMapping("/payments/orders") @PreAuthorize("hasAnyRole('PATIENT','CASHIER')")
    public CashierRepository.Payment createOrder(@RequestBody CreatePaymentRequest request,JwtAuthenticationToken auth) {
        String patientId=restrict(request.patientId(),auth);
        BigDecimal amount=request.amount()==null?new BigDecimal("0.01"):request.amount();
        if(amount.signum()<0) throw new IllegalArgumentException("支付金额不能为负数");
        return repository.createPaymentOrder(request.businessType(),request.businessId(),patientId,amount,
                request.paymentMethod()==null?"WECHAT":request.paymentMethod(),auth.getToken().getSubject());
    }
    @GetMapping("/payments") @PreAuthorize("hasAnyRole('PATIENT','CASHIER','ADMIN')")
    public List<CashierRepository.Payment> payments(@RequestParam(required=false) String patientId,@RequestParam(required=false) String businessId,JwtAuthenticationToken auth) {
        return repository.payments(restrict(patientId,auth),businessId);
    }
    @GetMapping("/refunds") @PreAuthorize("hasAnyRole('PATIENT','CASHIER','ADMIN')")
    public List<CashierRepository.Refund> refunds(@RequestParam(required=false) String patientId,@RequestParam(required=false) String businessId,JwtAuthenticationToken auth) {
        return repository.refunds(restrict(patientId,auth),businessId);
    }
    @PostMapping("/payments/test-callback")
    @PreAuthorize("hasAnyRole('PATIENT','CASHIER','ADMIN')")
    public CashierRepository.Payment testCallback(@RequestBody TestPaymentRequest request, JwtAuthenticationToken auth) {
        if (!testModeEnabled) throw new IllegalStateException("测试支付未启用");
        String patientId = restrict(request.patientId(), auth);
        if (patientId == null || patientId.isBlank()) throw new IllegalArgumentException("patientId 不能为空");
        if (request.businessType() == null || request.businessType().isBlank() || request.businessId() == null || request.businessId().isBlank()) {
            throw new IllegalArgumentException("businessType 和 businessId 不能为空");
        }
        String channel = request.channel() == null ? "WECHAT" : request.channel().toUpperCase();
        if (!List.of("WECHAT", "ALIPAY", "SIMULATED").contains(channel)) throw new IllegalArgumentException("不支持的测试支付渠道");
        String tradeNo = request.channelTradeNo() == null || request.channelTradeNo().isBlank()
                ? "test-" + UUID.randomUUID() : request.channelTradeNo();
        CashierRepository.Payment payment=repository.recordTestPayment(request.businessType(), request.businessId(), patientId,
                channel, auth.getToken().getSubject(), tradeNo);
        if("APPOINTMENT".equals(request.businessType())) {
            appointmentClient.confirm(request.businessId(),patientId,payment.paymentMethod(),payment.id());
        }
        if("MEDICAL_ORDER".equals(request.businessType())) medicalOrderClient.confirm(request.businessId(),patientId,payment.id());
        return payment;
    }
    @PostMapping("/payments/test-failure")
    @PreAuthorize("hasAnyRole('PATIENT','CASHIER','ADMIN')")
    public CashierRepository.Payment testFailure(@RequestBody TestPaymentRequest request,JwtAuthenticationToken auth) {
        if(!testModeEnabled) throw new IllegalStateException("测试支付未启用");
        String patientId=restrict(request.patientId(),auth);
        CashierRepository.Payment payment=repository.recordTestFailure(request.businessType(),request.businessId(),patientId,
                auth.getToken().getSubject(),"SIMULATED_FAILURE");
        if("APPOINTMENT".equals(request.businessType())) appointmentClient.fail(request.businessId(),patientId,payment.id());
        return payment;
    }
    private String restrict(String patientId,JwtAuthenticationToken auth) {
        if(!"PATIENT".equals(auth.getToken().getClaimAsString("role"))) return patientId;
        String subject=auth.getToken().getSubject(); if(patientId!=null&&!subject.equals(patientId)) throw new AccessDeniedException("患者只能查看自己的费用记录"); return subject;
    }
    public record TestPaymentRequest(String businessType, String businessId, String patientId,
                                     String channel, String channelTradeNo) {}
    public record CreatePaymentRequest(String businessType,String businessId,String patientId,
                                       BigDecimal amount,String paymentMethod) {}
}
