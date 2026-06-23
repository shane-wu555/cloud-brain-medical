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
    private final RestClient aiClient;
    private final String internalApiKey;
    public ScheduleController(DoctorCatalogRepository repository, @Value("${internal.api-key}") String internalApiKey,
            @Value("${services.appointment.base-url:http://localhost:8104}") String appointmentUrl,
            @Value("${services.ai.base-url:http://localhost:8000}") String aiUrl) {
        this.repository=repository; this.internalApiKey=internalApiKey;
        this.appointmentClient=RestClient.builder().baseUrl(appointmentUrl).build();
        this.aiClient=RestClient.builder().baseUrl(aiUrl).build();
    }
    @GetMapping public List<ScheduleDto> list(@RequestParam(required=false) String doctorId,
            @RequestParam(required=false) String departmentId) {
        Map<String,SlotDto> slots=slots().stream().collect(Collectors.toMap(SlotDto::scheduleId,Function.identity()));
        return repository.schedules(doctorId,departmentId).stream()
                .filter(s -> !"SUSPENDED".equals(s.status()))
                .map(s -> { SlotDto slot=slots.get(s.id()); return dto(s,slot==null?0:slot.booked(),slot==null?0:slot.locked()); }).toList();
    }
    @PostMapping @PreAuthorize("hasRole('ADMIN')")
    public ScheduleDto create(@RequestBody CreateScheduleRequest request) {
        var s=repository.createSchedule(request.doctorId(),request.departmentId(),LocalDate.parse(request.workDate()),request.period(),request.capacity());
        syncSlot(s.id(),s.capacity()); return dto(s,0,0);
    }
    @PostMapping("/ai-suggestions") @PreAuthorize("hasRole('ADMIN')")
    public AiScheduleResponse aiSuggestions(@RequestBody AiScheduleRequest request) {
        AiScheduleResponse response=aiClient.post().uri("/api/ai/schedule-suggestions").body(request)
                .retrieve().body(AiScheduleResponse.class);
        return response==null?new AiScheduleResponse(null,List.of()):response;
    }
    @PostMapping("/ai-suggestions/{suggestionId}/publish") @PreAuthorize("hasRole('ADMIN')")
    public ScheduleDto publishAiSuggestion(@PathVariable String suggestionId,@RequestBody PublishAiScheduleRequest request) {
        if(request.doctorId()==null||request.departmentId()==null||request.workDate()==null||request.period()==null) throw new IllegalArgumentException("AI 排班建议缺少必要字段");
        var s=repository.createSchedule(request.doctorId(),request.departmentId(),LocalDate.parse(request.workDate()),request.period(),request.capacity());
        syncSlot(s.id(),s.capacity());
        return dto(s,0,0);
    }
    @PutMapping("/{id}/suspend") @PreAuthorize("hasRole('ADMIN')")
    public ScheduleDto suspend(@PathVariable String id,@RequestBody SuspendRequest request) {
        SlotDto slot=slots().stream().filter(item->item.scheduleId().equals(id)).findFirst().orElse(null);
        var s=repository.suspendSchedule(id,request.reason());
        if(slot!=null) syncSlot(id,slot.booked()+slot.locked());
        return dto(s,slot==null?0:slot.booked(),slot==null?0:slot.locked());
    }
    @PutMapping("/{id}/reschedule") @PreAuthorize("hasRole('ADMIN')")
    public ScheduleDto reschedule(@PathVariable String id,@RequestBody RescheduleRequest request) {
        var s=repository.reschedule(id,LocalDate.parse(request.workDate()),request.period()); return dto(s,booked(id),0);
    }
    private void syncSlot(String id,int capacity) { appointmentClient.post().uri("/api/internal/appointment-slots")
            .header("X-Internal-Api-Key",internalApiKey).body(Map.of("scheduleId",id,"capacity",capacity)).retrieve().toBodilessEntity(); }
    private int booked(String id) { return slots().stream().filter(s->s.scheduleId().equals(id)).findFirst().map(SlotDto::booked).orElse(0); }
    private List<SlotDto> slots() { var result=appointmentClient.get().uri("/api/internal/appointment-slots")
            .header("X-Internal-Api-Key",internalApiKey).retrieve().body(new ParameterizedTypeReference<List<SlotDto>>(){}); return result==null?List.of():result; }
    private ScheduleDto dto(DoctorCatalogRepository.Schedule s,int booked,int locked) { return new ScheduleDto(s.id(),s.doctorId(),s.doctorName(),s.departmentId(),s.workDate().toString(),s.period(),s.capacity(),booked,locked,Math.max(0,s.capacity()-booked-locked),s.status()); }
    public record CreateScheduleRequest(String doctorId,String departmentId,String workDate,String period,int capacity) {}
    public record SuspendRequest(String reason) {}
    public record RescheduleRequest(String workDate,String period) {}
    public record ScheduleDto(String id,String doctorId,String doctorName,String departmentId,String workDate,String period,int capacity,int booked,int locked,int available,String status) {}
    public record SlotDto(String scheduleId,int capacity,int locked,int booked,int available) {}
    public record AiScheduleRequest(List<AiDoctorCandidate> candidates,List<AiScheduleDemand> demands) {}
    public record AiDoctorCandidate(String doctorId,String doctorName,String departmentId,String specialty,int weeklyCapacity,List<String> leaveDates) {}
    public record AiScheduleDemand(String departmentId,String workDate,String period,int expectedVisits,String riskLevel) {}
    public record AiScheduleResponse(String aiRecordId,List<AiScheduleSuggestion> suggestions) {}
    public record AiScheduleSuggestion(String suggestionId,String doctorId,String doctorName,String departmentId,String workDate,String period,int capacity,String reason,boolean requiresAdminConfirmation) {}
    public record PublishAiScheduleRequest(String aiRecordId,String doctorId,String departmentId,String workDate,String period,int capacity,String reason) {}
}
