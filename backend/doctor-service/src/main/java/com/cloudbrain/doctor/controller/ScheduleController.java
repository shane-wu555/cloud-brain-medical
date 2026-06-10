package com.cloudbrain.doctor.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {
    private final RestClient appointmentClient = RestClient.builder()
            .baseUrl("http://localhost:8104")
            .build();

    private final List<ScheduleDto> schedules = new ArrayList<>(List.of(
            new ScheduleDto("schedule-001", "doctor-001", "张医生", "dept-neuro", LocalDate.now().toString(), "上午", 20, 8),
            new ScheduleDto("schedule-002", "doctor-001", "张医生", "dept-neuro", LocalDate.now().plusDays(1).toString(), "下午", 18, 3),
            new ScheduleDto("schedule-003", "doctor-002", "李医生", "dept-imaging", LocalDate.now().toString(), "下午", 16, 6),
            new ScheduleDto("schedule-004", "doctor-003", "陈医生", "dept-general", LocalDate.now().toString(), "全天", 30, 12)));

    @GetMapping
    public List<ScheduleDto> list(
            @RequestParam(name = "doctorId", required = false) String doctorId,
            @RequestParam(name = "departmentId", required = false) String departmentId) {
        return schedules.stream()
                .filter(item -> Optional.ofNullable(doctorId).map(id -> id.equals(item.doctorId())).orElse(true))
                .filter(item -> Optional.ofNullable(departmentId).map(id -> id.equals(item.departmentId())).orElse(true))
                .toList();
    }

    @PostMapping
    public ScheduleDto create(@RequestBody CreateScheduleRequest request) {
        ScheduleDto schedule = new ScheduleDto(
                "schedule-" + (schedules.size() + 1),
                request.doctorId(),
                request.doctorName(),
                request.departmentId(),
                request.workDate(),
                request.period(),
                request.capacity(),
                0);
        schedules.add(schedule);
        syncSlot(schedule.id(), schedule.capacity());
        return schedule;
    }

    private void syncSlot(String scheduleId, int capacity) {
        try {
            appointmentClient.post()
                    .uri("/api/appointments/slots")
                    .body(Map.of("scheduleId", scheduleId, "capacity", capacity))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ignored) {
            // Local development can create schedules while the appointment service is restarting.
        }
    }

    public record CreateScheduleRequest(
            String doctorId,
            String doctorName,
            String departmentId,
            String workDate,
            String period,
            int capacity) {
    }

    public record ScheduleDto(
            String id,
            String doctorId,
            String doctorName,
            String departmentId,
            String workDate,
            String period,
            int capacity,
            int booked) {
    }
}
