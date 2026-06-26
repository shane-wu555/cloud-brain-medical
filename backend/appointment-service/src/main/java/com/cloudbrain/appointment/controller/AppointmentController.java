package com.cloudbrain.appointment.controller;

import com.cloudbrain.appointment.entity.Appointment;
import com.cloudbrain.appointment.entity.SlotInventory;
import com.cloudbrain.appointment.service.AppointmentService;
import com.cloudbrain.appointment.service.PatientVerificationClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {
    private final AppointmentService appointmentService;
    private final PatientVerificationClient patientVerificationClient;

    public AppointmentController(AppointmentService appointmentService, PatientVerificationClient patientVerificationClient) {
        this.appointmentService = appointmentService;
        this.patientVerificationClient = patientVerificationClient;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PATIENT','CASHIER','OUTPATIENT_DOCTOR','ADMIN')")
    public List<Appointment> list(
            @RequestParam(name = "doctorId", required = false) String doctorId,
            @RequestParam(name = "patientId", required = false) String patientId,
            @RequestParam(name = "status", required = false) String status,
            JwtAuthenticationToken authentication) {
        if ("PATIENT".equals(authentication.getToken().getClaimAsString("role"))) {
            String accountId = authentication.getToken().getSubject();
            if (patientId == null || patientId.isBlank()) {
                patientId = patientVerificationClient.boundPatientId(accountId);
                if (patientId == null || patientId.isBlank()) throw new AccessDeniedException("请先添加并绑定就诊人");
            }
            if (!patientVerificationClient.owns(accountId, patientId)) {
                throw new AccessDeniedException("患者只能查看自己账号名下就诊人的挂号记录");
            }
        }
        return appointmentService.list(doctorId, patientId, status);
    }

    @GetMapping("/slots")
    @PreAuthorize("isAuthenticated()")
    public List<SlotInventory> slots() {
        return appointmentService.slots();
    }

    @GetMapping("/queue/today")
    @PreAuthorize("hasRole('OUTPATIENT_DOCTOR')")
    public List<Appointment> todayQueue(JwtAuthenticationToken authentication) {
        return appointmentService.todayQueue(authentication.getToken().getSubject());
    }

    @PostMapping("/slots")
    @PreAuthorize("hasRole('ADMIN')")
    public SlotInventory syncSlot(@RequestBody SyncSlotRequest request) {
        return appointmentService.syncSlot(request.scheduleId(), request.capacity());
    }

    @PostMapping
    @PreAuthorize("hasRole('PATIENT')")
    public Appointment lockOnline(@RequestBody CreateAppointmentRequest request, JwtAuthenticationToken authentication) {
        String accountId = authentication.getToken().getSubject();
        if (!patientVerificationClient.hasBoundPatient(accountId)) throw new AccessDeniedException("请先添加并绑定就诊人");
        if (!patientVerificationClient.owns(accountId, request.patientId())) {
            throw new AccessDeniedException("只能为当前账号名下的就诊人创建挂号");
        }
        return appointmentService.lockOnline(request);
    }

    @PostMapping("/offline")
    @PreAuthorize("hasRole('CASHIER')")
    public Appointment createOffline(@RequestBody CreateAppointmentRequest request) {
        return appointmentService.createOffline(request);
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasRole('CASHIER')")
    public Appointment pay(@PathVariable("id") String id, @RequestBody PayRequest request, JwtAuthenticationToken authentication) {
        return appointmentService.pay(id, request.paymentMethod(), request.amount(), authentication.getToken().getSubject());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('PATIENT','CASHIER')")
    public Appointment cancel(@PathVariable("id") String id, JwtAuthenticationToken authentication) {
        String role = authentication.getToken().getClaimAsString("role");
        if ("PATIENT".equals(role)) {
            Appointment appointment = appointmentService.find(id);
            if (!patientVerificationClient.owns(authentication.getToken().getSubject(), appointment.getPatientId())) {
                throw new AccessDeniedException("患者只能操作自己账号名下就诊人的挂号记录");
            }
        }
        return appointmentService.cancel(id, "CASHIER".equals(role));
    }

    @PostMapping("/{id}/skip")
    @PreAuthorize("hasRole('OUTPATIENT_DOCTOR')")
    public Appointment skip(@PathVariable("id") String id, JwtAuthenticationToken authentication) {
        return appointmentService.skip(id, authentication.getToken().getSubject());
    }

    @PostMapping("/{id}/call")
    @PreAuthorize("hasRole('OUTPATIENT_DOCTOR')")
    public Appointment call(@PathVariable("id") String id, JwtAuthenticationToken authentication) {
        return appointmentService.call(id, authentication.getToken().getSubject());
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('OUTPATIENT_DOCTOR')")
    public Appointment start(@PathVariable("id") String id, JwtAuthenticationToken authentication) {
        return appointmentService.startVisit(id, authentication.getToken().getSubject());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('OUTPATIENT_DOCTOR')")
    public Appointment updateStatus(@PathVariable("id") String id, @RequestBody Map<String, String> body,
            JwtAuthenticationToken authentication) {
        return appointmentService.updateStatus(id, body.getOrDefault("status", "WAITING"), authentication.getToken().getSubject());
    }

    public record CreateAppointmentRequest(
            String scheduleId,
            String patientId,
            String patientName,
            String doctorId,
            String doctorName,
            String departmentId,
            String departmentName,
            String visitDate,
            String period,
            String triageSummary,
            String riskLevel,
            String recommendedDepartmentId) {
    }

    public record SyncSlotRequest(String scheduleId, int capacity) {
    }

    public record PayRequest(String paymentMethod, BigDecimal amount, String operatorId) {
    }
}
