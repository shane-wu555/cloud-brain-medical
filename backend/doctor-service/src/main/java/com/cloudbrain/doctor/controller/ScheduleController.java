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
    @GetMapping public List<ScheduleDto> list(@RequestParam(name="doctorId", required=false) String doctorId,
            @RequestParam(name="departmentId", required=false) String departmentId) {
        Map<String,SlotDto> slots=slots().stream().collect(Collectors.toMap(SlotDto::scheduleId,Function.identity()));
        List<DoctorCatalogRepository.Schedule> schedules = repository.schedules(doctorId,departmentId).stream()
                .filter(s -> !"SUSPENDED".equals(s.status()))
                .toList();
        List<DoctorCatalogRepository.ScheduleTimeSlot> allTimeSlots = repository.timeSlots(
                        schedules.stream().map(DoctorCatalogRepository.Schedule::id).toList());
        for (DoctorCatalogRepository.ScheduleTimeSlot slot : allTimeSlots) {
            if (!slots.containsKey(slot.id())) {
                syncSlot(slot.id(), slot.capacity());
            }
        }
        Map<String,List<DoctorCatalogRepository.ScheduleTimeSlot>> timeSlots = allTimeSlots.stream()
                .collect(Collectors.groupingBy(DoctorCatalogRepository.ScheduleTimeSlot::scheduleId));
        return schedules.stream()
                .map(s -> dto(s,timeSlots.getOrDefault(s.id(),List.of()),slots)).toList();
    }
    @PostMapping @PreAuthorize("hasRole('ADMIN')")
    public ScheduleDto create(@RequestBody CreateScheduleRequest request) {
        var s=repository.createSchedule(request.doctorId(),request.departmentId(),LocalDate.parse(request.workDate()),request.period(),request.capacity());
        syncSlots(repository.timeSlots(s.id()), Map.of()); return dto(s,repository.timeSlots(s.id()),Map.of());
    }
    @PostMapping("/ai-suggestions") @PreAuthorize("hasRole('ADMIN')")
    public AiScheduleResponse aiSuggestions(@RequestBody AiScheduleRequest request) {
        AiScheduleResponse response=aiClient.post().uri("/api/ai/schedule-suggestions").body(request)
                .retrieve().body(AiScheduleResponse.class);
        return response==null?new AiScheduleResponse(null,List.of()):response;
    }
    @PostMapping("/ai-suggestions/{suggestionId}/publish") @PreAuthorize("hasRole('ADMIN')")
    public ScheduleDto publishAiSuggestion(@PathVariable("suggestionId") String suggestionId,@RequestBody PublishAiScheduleRequest request) {
        if(request.doctorId()==null||request.departmentId()==null||request.workDate()==null||request.period()==null) throw new IllegalArgumentException("AI 排班建议缺少必要字段");
        var s=repository.createSchedule(request.doctorId(),request.departmentId(),LocalDate.parse(request.workDate()),request.period(),request.capacity());
        syncSlots(repository.timeSlots(s.id()), Map.of());
        return dto(s,repository.timeSlots(s.id()),Map.of());
    }
    @PutMapping("/{id}/suspend") @PreAuthorize("hasRole('ADMIN')")
    public ScheduleDto suspend(@PathVariable("id") String id,@RequestBody SuspendRequest request) {
        Map<String,SlotDto> slots=slots().stream().collect(Collectors.toMap(SlotDto::scheduleId,Function.identity()));
        List<DoctorCatalogRepository.ScheduleTimeSlot> timeSlots=repository.timeSlots(id);
        var s=repository.suspendSchedule(id,request.reason());
        syncSlots(timeSlots,timeSlots.stream().collect(Collectors.toMap(DoctorCatalogRepository.ScheduleTimeSlot::id,
                slot -> {
                    SlotDto current=slots.get(slot.id());
                    int reserved=current==null?0:current.booked()+current.locked();
                    return new SlotDto(slot.id(),reserved,current==null?0:current.locked(),current==null?0:current.booked(),0);
                })));
        return dto(s,timeSlots,slots);
    }
    @PutMapping("/{id}/reschedule") @PreAuthorize("hasRole('ADMIN')")
    public ScheduleDto reschedule(@PathVariable("id") String id,@RequestBody RescheduleRequest request) {
        var s=repository.reschedule(id,LocalDate.parse(request.workDate()),request.period());
        Map<String,SlotDto> slots=slots().stream().collect(Collectors.toMap(SlotDto::scheduleId,Function.identity()));
        return dto(s,repository.timeSlots(id),slots);
    }
    private void syncSlot(String id,int capacity) { appointmentClient.post().uri("/api/internal/appointment-slots")
            .header("X-Internal-Api-Key",internalApiKey).body(Map.of("scheduleId",id,"capacity",capacity)).retrieve().toBodilessEntity(); }
    private void syncSlots(List<DoctorCatalogRepository.ScheduleTimeSlot> timeSlots, Map<String,SlotDto> overrides) {
        for (DoctorCatalogRepository.ScheduleTimeSlot slot : timeSlots) {
            SlotDto override=overrides.get(slot.id());
            syncSlot(slot.id(),override==null?slot.capacity():override.capacity());
        }
    }
    private List<SlotDto> slots() { var result=appointmentClient.get().uri("/api/internal/appointment-slots")
            .header("X-Internal-Api-Key",internalApiKey).retrieve().body(new ParameterizedTypeReference<List<SlotDto>>(){}); return result==null?List.of():result; }
    private ScheduleDto dto(DoctorCatalogRepository.Schedule s,List<DoctorCatalogRepository.ScheduleTimeSlot> timeSlots,Map<String,SlotDto> slots) {
        List<TimeSlotDto> items=timeSlots.stream().map(slot -> {
            SlotDto inventory=slots.get(slot.id());
            int booked=inventory==null?0:inventory.booked();
            int locked=inventory==null?0:inventory.locked();
            int capacity=inventory==null?slot.capacity():inventory.capacity();
            return new TimeSlotDto(slot.id(),slot.startTime().toString(),capacity,booked,locked,Math.max(0,capacity-booked-locked));
        }).toList();
        int capacity=items.stream().mapToInt(TimeSlotDto::capacity).sum();
        int booked=items.stream().mapToInt(TimeSlotDto::booked).sum();
        int locked=items.stream().mapToInt(TimeSlotDto::locked).sum();
        int available=items.stream().mapToInt(TimeSlotDto::available).sum();
        return new ScheduleDto(s.id(),s.doctorId(),s.doctorName(),s.departmentId(),s.workDate().toString(),periodLabel(s.period()),capacity,booked,locked,available,s.status(),items);
    }
    private String periodLabel(String period) {
        String value=period==null?"":period.trim().toUpperCase();
        if("MORNING".equals(value)||"上午".equals(period)) return "上午";
        if("AFTERNOON".equals(value)||"下午".equals(period)) return "下午";
        return "全天";
    }
    public record CreateScheduleRequest(String doctorId,String departmentId,String workDate,String period,int capacity) {}
    public record SuspendRequest(String reason) {}
    public record RescheduleRequest(String workDate,String period) {}
    public record ScheduleDto(String id,String doctorId,String doctorName,String departmentId,String workDate,String period,int capacity,int booked,int locked,int available,String status,List<TimeSlotDto> timeSlots) {}
    public record TimeSlotDto(String id,String startTime,int capacity,int booked,int locked,int available) {}
    public record SlotDto(String scheduleId,int capacity,int locked,int booked,int available) {}
    public record AiScheduleRequest(List<AiDoctorCandidate> candidates,List<AiScheduleDemand> demands) {}
    public record AiDoctorCandidate(String doctorId,String doctorName,String departmentId,String specialty,int weeklyCapacity,List<String> leaveDates) {}
    public record AiScheduleDemand(String departmentId,String workDate,String period,int expectedVisits,String riskLevel) {}
    public record AiScheduleResponse(String aiRecordId,List<AiScheduleSuggestion> suggestions) {}
    public record AiScheduleSuggestion(String suggestionId,String doctorId,String doctorName,String departmentId,String workDate,String period,int capacity,String reason,boolean requiresAdminConfirmation) {}
    public record PublishAiScheduleRequest(String aiRecordId,String doctorId,String departmentId,String workDate,String period,int capacity,String reason) {}
}
