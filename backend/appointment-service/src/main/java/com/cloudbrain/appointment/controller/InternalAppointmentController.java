package com.cloudbrain.appointment.controller;

import com.cloudbrain.appointment.entity.Appointment;
import com.cloudbrain.appointment.service.AppointmentService;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/internal/appointments")
public class InternalAppointmentController {
    private final AppointmentService service; private final String internalApiKey;
    public InternalAppointmentController(AppointmentService service,@Value("${internal.api-key}") String key) {
        this.service=service; this.internalApiKey=key;
    }
    @PostMapping("/{id}/payment-confirmation")
    public Appointment confirm(@PathVariable String id,@RequestHeader(name="X-Internal-Api-Key",required=false) String key,
            @RequestBody PaymentConfirmation request) {
        if(!internalApiKey.equals(key)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"内部接口认证失败");
        service.validatePatientAccess(id,request.patientId(),"PATIENT");
        return service.pay(id,request.paymentMethod(),new BigDecimal("0.01"),request.patientId());
    }
    @PostMapping("/{id}/payment-failure")
    public Appointment fail(@PathVariable String id,@RequestHeader(name="X-Internal-Api-Key",required=false) String key,
            @RequestBody PaymentFailure request) {
        if(!internalApiKey.equals(key)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"内部接口认证失败");
        return service.failPayment(id,request.patientId());
    }
    public record PaymentConfirmation(String patientId,String paymentMethod,String paymentOrderId) {}
    public record PaymentFailure(String patientId,String paymentOrderId) {}
}
