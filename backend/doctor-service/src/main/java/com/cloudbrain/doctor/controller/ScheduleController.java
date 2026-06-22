package com.cloudbrain.doctor.controller;

import com.cloudbrain.doctor.repository.DoctorCatalogRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final DoctorCatalogRepository repository;
    private final RestClient appointmentClient;
    private final String internalApiKey;

    public ScheduleController(
            DoctorCatalogRepository repository,
            @Value("${internal.api-key}") String internalApiKey,
            @Value("${services.appointment.base-url:http://localhost:8104}") String appointmentUrl) {
        this.repository = repository;
        this.internalApiKey = internalApiKey;
        this.appointmentClient = RestClient.builder().baseUrl(appointmentUrl).build();
    }

    @GetMapping
    public List<ScheduleDto> list(
            @RequestParam(required = false) String doctorId,
            @RequestParam(required = false) String departmentId) {
        Map<String, SlotDto> slots = slots().stream().collect(Collectors.toMap(SlotDto::scheduleId, Function.identity()));
        return repository.schedules(doctorId, departmentId).stream().map(schedule -> {
            SlotDto slot = slots.get(schedule.id());
            return new ScheduleDto(schedule.id(), schedule.doctorId(), schedule.doctorName(), schedule.departmentId(),
                    schedule.workDate().toString(), schedule.period(), schedule.capacity(), slot == null ? 0 : slot.booked());
        }).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ScheduleDto create(@RequestBody CreateScheduleRequest request) {
        DoctorCatalogRepository.Schedule schedule = repository.createSchedule(
                request.doctorId(), request.departmentId(), LocalDate.parse(request.workDate()), request.period(), request.capacity());
        appointmentClient.post().uri("/api/internal/appointment-slots")
                .header("X-Internal-Api-Key", internalApiKey)
                .body(Map.of("scheduleId", schedule.id(), "capacity", schedule.capacity())).retrieve().toBodilessEntity();
        return new ScheduleDto(schedule.id(), schedule.doctorId(), schedule.doctorName(), schedule.departmentId(),
                schedule.workDate().toString(), schedule.period(), schedule.capacity(), 0);
    }

    private List<SlotDto> slots() {
        List<SlotDto> result = appointmentClient.get().uri("/api/internal/appointment-slots")
                .header("X-Internal-Api-Key", internalApiKey).retrieve()
                .body(new ParameterizedTypeReference<List<SlotDto>>() {});
        return result == null ? List.of() : result;
    }

    public record CreateScheduleRequest(String doctorId,String departmentId,String workDate,String period,int capacity) {}
    public record ScheduleDto(String id,String doctorId,String doctorName,String departmentId,String workDate,String period,int capacity,int booked) {}
    public record SlotDto(String scheduleId,int capacity,int locked,int booked,int available) {}
}
