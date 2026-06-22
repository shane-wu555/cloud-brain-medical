package com.cloudbrain.doctor.controller;

import com.cloudbrain.doctor.repository.DoctorCatalogRepository;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

@RestController @RequestMapping("/api/schedules")
public class ScheduleController {
    private final DoctorCatalogRepository repository;
    private final RestClient appointmentClient;
    private final String internalApiKey;
    public ScheduleController(DoctorCatalogRepository repository, @Value("${internal.api-key}") String internalApiKey,
            @Value("${services.appointment.base-url:http://localhost:8104}") String appointmentUrl) {
        this.repository=repository; this.internalApiKey=internalApiKey;
        this.appointmentClient=RestClient.builder().baseUrl(appointmentUrl).build();
    }
    @GetMapping public List<ScheduleDto> list(@RequestParam(required=false) String doctorId,
            @RequestParam(required=false) String departmentId) {
        Map<String,SlotDto> slots=slots().stream().collect(Collectors.toMap(SlotDto::scheduleId,Function.identity()));
        return repository.schedules(doctorId,departmentId).stream()
                .filter(s -> !"SUSPENDED".equals(s.status()))
                .map(s -> dto(s,Optional.ofNullable(slots.get(s.id())).map(SlotDto::booked).orElse(0))).toList();
    }
    @PostMapping @PreAuthorize("hasRole('ADMIN')")
    public ScheduleDto create(@RequestBody CreateScheduleRequest request) {
        var s=repository.createSchedule(request.doctorId(),request.departmentId(),LocalDate.parse(request.workDate()),request.period(),request.capacity());
        syncSlot(s.id(),s.capacity()); return dto(s,0);
    }
    @PutMapping("/{id}/suspend") @PreAuthorize("hasRole('ADMIN')")
    public ScheduleDto suspend(@PathVariable String id,@RequestBody SuspendRequest request) {
        SlotDto slot=slots().stream().filter(item->item.scheduleId().equals(id)).findFirst().orElse(null);
        var s=repository.suspendSchedule(id,request.reason());
        if(slot!=null) syncSlot(id,slot.booked()+slot.locked());
        return dto(s,slot==null?0:slot.booked());
    }
    @PutMapping("/{id}/reschedule") @PreAuthorize("hasRole('ADMIN')")
    public ScheduleDto reschedule(@PathVariable String id,@RequestBody RescheduleRequest request) {
        var s=repository.reschedule(id,LocalDate.parse(request.workDate()),request.period()); return dto(s,booked(id));
    }
    private void syncSlot(String id,int capacity) { appointmentClient.post().uri("/api/internal/appointment-slots")
            .header("X-Internal-Api-Key",internalApiKey).body(Map.of("scheduleId",id,"capacity",capacity)).retrieve().toBodilessEntity(); }
    private int booked(String id) { return slots().stream().filter(s->s.scheduleId().equals(id)).findFirst().map(SlotDto::booked).orElse(0); }
    private List<SlotDto> slots() { var result=appointmentClient.get().uri("/api/internal/appointment-slots")
            .header("X-Internal-Api-Key",internalApiKey).retrieve().body(new ParameterizedTypeReference<List<SlotDto>>(){}); return result==null?List.of():result; }
    private ScheduleDto dto(DoctorCatalogRepository.Schedule s,int booked) { return new ScheduleDto(s.id(),s.doctorId(),s.doctorName(),s.departmentId(),s.workDate().toString(),s.period(),s.capacity(),booked,s.status()); }
    public record CreateScheduleRequest(String doctorId,String departmentId,String workDate,String period,int capacity) {}
    public record SuspendRequest(String reason) {}
    public record RescheduleRequest(String workDate,String period) {}
    public record ScheduleDto(String id,String doctorId,String doctorName,String departmentId,String workDate,String period,int capacity,int booked,String status) {}
    public record SlotDto(String scheduleId,int capacity,int locked,int booked,int available) {}
}
