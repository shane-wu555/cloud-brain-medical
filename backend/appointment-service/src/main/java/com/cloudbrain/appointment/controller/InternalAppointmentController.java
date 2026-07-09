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
    public Appointment confirm(@PathVariable("id") String id,@RequestHeader(name="X-Internal-Api-Key",required=false) String key,
            @RequestBody PaymentConfirmation request) {
        if(!internalApiKey.equals(key)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"内部接口认证失败");
        service.validatePatientAccess(id,request.patientId(),"PATIENT");
        return service.pay(id,request.paymentMethod(),new BigDecimal("0.01"),request.patientId());
    }
    @PostMapping("/{id}/payment-failure")
    public Appointment fail(@PathVariable("id") String id,@RequestHeader(name="X-Internal-Api-Key",required=false) String key,
            @RequestBody PaymentFailure request) {
        if(!internalApiKey.equals(key)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"内部接口认证失败");
        return service.failPayment(id,request.patientId());
    }
    @PostMapping("/{id}/refund-confirmation")
    public Appointment refund(@PathVariable("id") String id,@RequestHeader(name="X-Internal-Api-Key",required=false) String key,
            @RequestBody RefundConfirmation request) {
        if(!internalApiKey.equals(key)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"鍐呴儴鎺ュ彛璁よ瘉澶辫触");
        service.validatePatientAccess(id,request.patientId(),"PATIENT");
        return service.cancel(id,true);
    }
    @PostMapping("/{id}/revisit") public Appointment revisit(@PathVariable("id") String id,@RequestHeader(name="X-Internal-Api-Key",required=false)String key){if(!internalApiKey.equals(key))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return service.enterRevisit(id);}
    @GetMapping("/scheduling-history-summary")
    public Object schedulingHistorySummary(@RequestHeader(name="X-Internal-Api-Key",required=false) String key,
            @RequestParam(name="lookbackDays", defaultValue="90") int lookbackDays) {
        if(!internalApiKey.equals(key)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"内部接口认证失败");
        return service.schedulingHistorySummary(lookbackDays);
    }
    public record PaymentConfirmation(String patientId,String paymentMethod,String paymentOrderId) {}
    public record PaymentFailure(String patientId,String paymentOrderId) {}
    public record RefundConfirmation(String patientId,String operatorId) {}
}
