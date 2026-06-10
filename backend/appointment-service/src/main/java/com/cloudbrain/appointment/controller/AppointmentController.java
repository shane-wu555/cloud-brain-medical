package com.cloudbrain.appointment.controller;

import com.cloudbrain.appointment.entity.Appointment;
import com.cloudbrain.appointment.entity.SlotInventory;
import com.cloudbrain.appointment.service.AppointmentService;
import java.util.List;
import java.util.Map;
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

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping
    public List<Appointment> list(
            @RequestParam(name = "doctorId", required = false) String doctorId,
            @RequestParam(name = "patientId", required = false) String patientId,
            @RequestParam(name = "status", required = false) String status) {
        return appointmentService.list(doctorId, patientId, status);
    }

    @GetMapping("/slots")
    public List<SlotInventory> slots() {
        return appointmentService.slots();
    }

    @PostMapping("/slots")
    public SlotInventory syncSlot(@RequestBody SyncSlotRequest request) {
        return appointmentService.syncSlot(request.scheduleId(), request.capacity());
    }

    @PostMapping
    public Appointment lockOnline(@RequestBody CreateAppointmentRequest request) {
        return appointmentService.lockOnline(request);
    }

    @PostMapping("/offline")
    public Appointment createOffline(@RequestBody CreateAppointmentRequest request) {
        return appointmentService.createOffline(request);
    }

    @PostMapping("/{id}/pay")
    public Appointment pay(@PathVariable("id") String id, @RequestBody Map<String, String> body) {
        return appointmentService.pay(id, body.get("paymentMethod"));
    }

    @PostMapping("/{id}/cancel")
    public Appointment cancel(@PathVariable("id") String id) {
        return appointmentService.cancel(id);
    }

    @PostMapping("/{id}/skip")
    public Appointment skip(@PathVariable("id") String id) {
        return appointmentService.skip(id);
    }

    @PatchMapping("/{id}/status")
    public Appointment updateStatus(@PathVariable("id") String id, @RequestBody Map<String, String> body) {
        return appointmentService.updateStatus(id, body.getOrDefault("status", "WAITING"));
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
}
