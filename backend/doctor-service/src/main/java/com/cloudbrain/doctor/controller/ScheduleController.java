package com.cloudbrain.doctor.controller;

import com.cloudbrain.doctor.repository.DoctorCatalogRepository;
import com.fasterxml.jackson.annotation.JsonAlias;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController @RequestMapping("/api/schedules")
public class ScheduleController {
    private static final Logger log=LoggerFactory.getLogger(ScheduleController.class);
    private static final int REPLAN_START_DAYS=7;
    private static final int REPLAN_DAYS=8;
    private final DoctorCatalogRepository repository;
    private final RestClient appointmentClient;
    private final RestClient aiClient;
    private final String internalApiKey;
    public ScheduleController(DoctorCatalogRepository repository, @Value("${internal.api-key}") String internalApiKey,
            @Value("${services.appointment.base-url:http://localhost:8104}") String appointmentUrl,
            @Value("${services.ai.base-url:http://localhost:8000}") String aiUrl) {
        this.repository=repository; this.internalApiKey=internalApiKey;
        this.appointmentClient=RestClient.builder().baseUrl(appointmentUrl).build();
        SimpleClientHttpRequestFactory aiRequestFactory=new SimpleClientHttpRequestFactory();
        aiRequestFactory.setConnectTimeout(10000);
        aiRequestFactory.setReadTimeout(180000);
        this.aiClient=RestClient.builder().requestFactory(aiRequestFactory).baseUrl(aiUrl).build();
    }
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,String> badRequest(IllegalArgumentException exception) {
        return Map.of("message",exception.getMessage());
    }
    @GetMapping public List<ScheduleDto> list(@RequestParam(name="doctorId", required=false) String doctorId,
            @RequestParam(name="departmentId", required=false) String departmentId,
            @RequestParam(name="bookingWindowOnly", defaultValue="true") boolean bookingWindowOnly) {
        Map<String,SlotDto> slots=slotsById();
        LocalDate today=LocalDate.now();
        List<DoctorCatalogRepository.Schedule> schedules = repository.schedules(doctorId,departmentId).stream()
                .filter(s -> !"SUSPENDED".equals(s.status()))
                .filter(s -> !bookingWindowOnly || (!s.workDate().isBefore(today) && !s.workDate().isAfter(today.plusDays(6))))
                .toList();
        List<DoctorCatalogRepository.ScheduleTimeSlot> allTimeSlots = repository.timeSlots(
                        schedules.stream().map(DoctorCatalogRepository.Schedule::id).toList());
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
    public AiScheduleResponse aiSuggestions(@RequestBody(required=false) AiScheduleRequest request) {
        return requestAiSuggestions(request);
    }
    private AiScheduleResponse requestAiSuggestions(AiScheduleRequest request) {
        AiScheduleRequest normalizedRequest=normalizeAiScheduleRequest(request);
        long startedAt=System.currentTimeMillis();
        Map<String,Object> payload=aiSchedulePayload(normalizedRequest);
        log.info("AI schedule request prepared: candidates={}, demands={}, departments={}, unavailableSlots={}",
                normalizedRequest.candidates().size(),normalizedRequest.demands().size(),
                normalizedRequest.demands().stream().map(AiScheduleDemand::departmentId).distinct().count(),
                normalizedRequest.candidates().stream().mapToInt(candidate -> Optional.ofNullable(candidate.unavailableSlots()).orElse(List.of()).size()).sum());
        try {
            Map<String,Object> response=aiClient.post().uri("/api/ai/schedule-suggestions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve().body(new ParameterizedTypeReference<Map<String,Object>>(){});
            AiScheduleResponse mapped=mapAiScheduleResponse(response);
            log.info("AI schedule response mapped: elapsedMs={}, rawSuggestions={}, mappedSuggestions={}, fallbackUsed={}, provider={}, model={}, ragSources={}",
                    System.currentTimeMillis()-startedAt,objectListSize(response==null?null:response.get("suggestions")),
                    mapped.suggestions().size(),mapped.fallbackUsed(),mapped.provider(),mapped.model(),mapped.knowledgeSources().size());
            return mapped;
        } catch (RestClientResponseException exception) {
            log.warn("AI schedule service rejected request: elapsedMs={}, status={}, body={}",
                    System.currentTimeMillis()-startedAt,exception.getStatusCode(),exception.getResponseBodyAsString());
            return fallbackAiSuggestions(normalizedRequest, "AI 服务拒绝请求，已使用本地均衡规则生成待确认建议");
        } catch (RestClientException exception) {
            log.warn("AI schedule service unavailable: elapsedMs={}, message={}",System.currentTimeMillis()-startedAt,exception.getMessage());
            return fallbackAiSuggestions(normalizedRequest, "AI 服务暂不可用，已使用本地均衡规则生成待确认建议");
        }
    }
    @GetMapping("/ai-replan-preview") @PreAuthorize("hasRole('ADMIN')")
    public AiScheduleResponse aiReplanPreview(@RequestParam(name="departmentId", required=false) String departmentId,
            @RequestParam(name="baseVisits", defaultValue="24") int baseVisits,
            @RequestParam(name="riskLevel", defaultValue="MEDIUM") String riskLevel,
            @RequestParam(name="weekendPeak", defaultValue="true") boolean weekendPeak,
            @RequestParam(name="weekendIncrease", defaultValue="35") int weekendIncrease,
            @RequestParam(name="morningPeak", defaultValue="true") boolean morningPeak,
            @RequestParam(name="morningIncrease", defaultValue="25") int morningIncrease,
            @RequestParam(name="force", defaultValue="false") boolean force) {
        LocalDate start=LocalDate.now().plusDays(REPLAN_START_DAYS);
        LocalDate end=start.plusDays(REPLAN_DAYS-1);
        List<DoctorCatalogRepository.Department> departments=repository.schedulingDepartments().stream()
                .filter(department -> departmentId==null||departmentId.isBlank()||department.id().equals(departmentId))
                .toList();
        List<DoctorCatalogRepository.DoctorEvent> events=repository.doctorEvents(start,end);
        List<DoctorCatalogRepository.Department> departmentsNeedingReplan=departmentsNeedingReplan(departments,start,end,events);
        List<DoctorCatalogRepository.Department> targetDepartments=departmentsNeedingReplan;
        if(targetDepartments.isEmpty()) {
            if(force&&departmentId!=null&&!departmentId.isBlank()) {
                targetDepartments=departments;
                log.info("AI schedule replan forced for selected department: departmentId={}, window={}..{}",departmentId,start,end);
            } else {
                log.info("AI schedule replan skipped: window {} to {} already covered and has no event conflicts",start,end);
                return new AiScheduleResponse(null,List.of(),"backend","not-required",false,List.of());
            }
        }
        List<AiDoctorCandidate> candidates=new ArrayList<>();
        List<AiScheduleDemand> demands=new ArrayList<>();
        for(DoctorCatalogRepository.Department department:targetDepartments) {
            List<DoctorCatalogRepository.Doctor> doctors=repository.doctors(department.id());
            for(DoctorCatalogRepository.Doctor doctor:doctors) {
                List<DoctorUnavailableSlot> unavailableSlots=events.stream()
                        .filter(event -> event.doctorId().equals(doctor.id()))
                        .flatMap(event -> event.dates().stream()
                                .flatMap(date -> event.periods().stream().map(period ->
                                        new DoctorUnavailableSlot(date.toString(),period,event.eventType()))))
                        .toList();
                candidates.add(new AiDoctorCandidate(
                        doctor.id(),doctor.name(),doctor.departmentId(),doctor.specialty()==null?"":doctor.specialty(),
                        40,
                        unavailableSlots.stream().filter(slot -> "LEAVE".equals(slot.type())).map(DoctorUnavailableSlot::date).distinct().toList(),
                        unavailableSlots.stream().filter(slot -> "SURGERY".equals(slot.type())).map(DoctorUnavailableSlot::date).distinct().toList(),
                        unavailableSlots));
            }
            for(LocalDate date=start;!date.isAfter(end);date=date.plusDays(1)) {
                demands.add(new AiScheduleDemand(department.id(),date.toString(),"上午",
                        expectedVisits(date,"上午",baseVisits,weekendPeak,weekendIncrease,morningPeak,morningIncrease),riskLevel,null));
                demands.add(new AiScheduleDemand(department.id(),date.toString(),"下午",
                        expectedVisits(date,"下午",baseVisits,weekendPeak,weekendIncrease,morningPeak,morningIncrease),riskLevel,null));
            }
        }
        log.info("AI schedule replan preview request: force={}, selectedDepartment={}, checkedDepartments={}, targetDepartments={}, candidates={}, demands={}, events={}, window={}..{}",
                force,departmentId,departments.size(),targetDepartments.stream().map(DoctorCatalogRepository.Department::id).toList(),
                candidates.size(),demands.size(),events.size(),start,end);
        if(candidates.isEmpty()||demands.isEmpty()) return new AiScheduleResponse(null,List.of(),null,null,false,List.of());
        return requestAiSuggestions(new AiScheduleRequest(candidates,demands));
    }
    @PostMapping("/ai-suggestions/{suggestionId}/publish") @PreAuthorize("hasRole('ADMIN')")
    public ScheduleDto publishAiSuggestion(@PathVariable("suggestionId") String suggestionId,@RequestBody PublishAiScheduleRequest request) {
        if(request.doctorId()==null||request.departmentId()==null||request.workDate()==null||request.period()==null) throw new IllegalArgumentException("AI 排班建议缺少必要字段");
        var s=repository.createSchedule(request.doctorId(),request.departmentId(),LocalDate.parse(request.workDate()),request.period(),request.capacity());
        syncSlots(repository.timeSlots(s.id()), Map.of());
        return dto(s,repository.timeSlots(s.id()),Map.of());
    }
    @PostMapping("/ai-suggestions/publish-batch") @PreAuthorize("hasRole('ADMIN')")
    public List<ScheduleDto> publishAiSuggestions(@RequestBody PublishAiScheduleBatchRequest request) {
        List<PublishAiScheduleRequest> suggestions=Optional.ofNullable(request.suggestions()).orElse(List.of());
        if(suggestions.isEmpty()) return List.of();
        Set<String> usedDoctorDates=new HashSet<>();
        List<PublishAiScheduleRequest> validSuggestions=suggestions.stream()
                .filter(item -> item.departmentId()!=null&&item.workDate()!=null&&item.period()!=null&&item.doctorId()!=null)
                .filter(item -> usedDoctorDates.add(item.doctorId()+":"+item.workDate()))
                .toList();
        Map<String,List<PublishAiScheduleRequest>> byDepartment=validSuggestions.stream()
                .collect(Collectors.groupingBy(PublishAiScheduleRequest::departmentId));
        List<ScheduleDto> published=new ArrayList<>();
        List<DoctorCatalogRepository.ScheduleTimeSlot> slotsToSync=new ArrayList<>();
        for(Map.Entry<String,List<PublishAiScheduleRequest>> entry:byDepartment.entrySet()) {
            LocalDate start=entry.getValue().stream().map(item -> LocalDate.parse(item.workDate())).min(LocalDate::compareTo).orElseThrow();
            LocalDate end=entry.getValue().stream().map(item -> LocalDate.parse(item.workDate())).max(LocalDate::compareTo).orElseThrow();
            repository.deleteSchedulesForDepartmentWindow(entry.getKey(),start,end);
            for(PublishAiScheduleRequest item:entry.getValue()) {
                var s=repository.createSchedule(item.doctorId(),item.departmentId(),LocalDate.parse(item.workDate()),item.period(),item.capacity());
                List<DoctorCatalogRepository.ScheduleTimeSlot> timeSlots=repository.timeSlots(s.id());
                slotsToSync.addAll(timeSlots);
                published.add(dto(s,timeSlots,Map.of()));
            }
        }
        syncSlotsBatch(slotsToSync);
        return published;
    }
    @PutMapping("/{id}/suspend") @PreAuthorize("hasRole('ADMIN')")
    public ScheduleDto suspend(@PathVariable("id") String id,@RequestBody SuspendRequest request) {
        Map<String,SlotDto> slots=slotsById();
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
        Map<String,SlotDto> slots=slotsById();
        return dto(s,repository.timeSlots(id),slots);
    }
    private void syncSlot(String id,int capacity) {
        try {
            appointmentClient.post().uri("/api/internal/appointment-slots")
                    .header("X-Internal-Api-Key",internalApiKey).body(Map.of("scheduleId",id,"capacity",capacity)).retrieve().toBodilessEntity();
        } catch (RestClientException exception) {
            log.warn("Appointment slot sync failed: scheduleSlotId={}, capacity={}, message={}",id,capacity,exception.getMessage());
        }
    }
    private void syncSlots(List<DoctorCatalogRepository.ScheduleTimeSlot> timeSlots, Map<String,SlotDto> overrides) {
        for (DoctorCatalogRepository.ScheduleTimeSlot slot : timeSlots) {
            SlotDto override=overrides.get(slot.id());
            syncSlot(slot.id(),override==null?slot.capacity():override.capacity());
        }
    }
    private void syncSlotsBatch(List<DoctorCatalogRepository.ScheduleTimeSlot> timeSlots) {
        if(timeSlots==null||timeSlots.isEmpty()) return;
        List<Map<String,Object>> payload=timeSlots.stream()
                .map(slot -> Map.<String,Object>of("scheduleId",slot.id(),"capacity",slot.capacity()))
                .toList();
        try {
            appointmentClient.post().uri("/api/internal/appointment-slots/batch")
                    .header("X-Internal-Api-Key",internalApiKey).body(payload).retrieve().toBodilessEntity();
        } catch (RestClientException exception) {
            log.warn("Appointment slot batch sync failed: slots={}, message={}",payload.size(),exception.getMessage());
        }
    }
    private List<SlotDto> slots() {
        try {
            var result=appointmentClient.get().uri("/api/internal/appointment-slots")
                    .header("X-Internal-Api-Key",internalApiKey).retrieve().body(new ParameterizedTypeReference<List<SlotDto>>(){});
            return result==null?List.of():result;
        } catch (RestClientException exception) {
            log.warn("Appointment slot inventory query failed; schedule list will use schedule capacity only: message={}",exception.getMessage());
            return List.of();
        }
    }
    private Map<String,SlotDto> slotsById() {
        return slots().stream()
                .filter(slot -> slot.scheduleId() != null && !slot.scheduleId().isBlank())
                .collect(Collectors.toMap(SlotDto::scheduleId,Function.identity(),(first, ignored) -> first));
    }
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
    private Map<String,Object> aiSchedulePayload(AiScheduleRequest request) {
        Map<String,Object> payload=new LinkedHashMap<>();
        payload.put("candidates", Optional.ofNullable(request.candidates()).orElse(List.of()).stream().map(this::aiDoctorPayload).toList());
        payload.put("demands", Optional.ofNullable(request.demands()).orElse(List.of()).stream().map(this::aiDemandPayload).toList());
        return payload;
    }
    private Map<String,Object> aiDoctorPayload(AiDoctorCandidate candidate) {
        Map<String,Object> payload=new LinkedHashMap<>();
        payload.put("doctorId", candidate.doctorId());
        payload.put("doctorName", candidate.doctorName());
        payload.put("departmentId", candidate.departmentId());
        payload.put("specialty", candidate.specialty()==null?"":candidate.specialty());
        payload.put("weeklyCapacity", candidate.weeklyCapacity());
        payload.put("leaveDates", Optional.ofNullable(candidate.leaveDates()).orElse(List.of()));
        payload.put("surgeryDates", Optional.ofNullable(candidate.surgeryDates()).orElse(List.of()));
        payload.put("unavailableSlots", Optional.ofNullable(candidate.unavailableSlots()).orElse(List.of()).stream().map(this::unavailableSlotPayload).toList());
        return payload;
    }
    private Map<String,Object> unavailableSlotPayload(DoctorUnavailableSlot slot) {
        Map<String,Object> payload=new LinkedHashMap<>();
        payload.put("date", slot.date());
        payload.put("period", slot.period());
        payload.put("type", slot.type());
        return payload;
    }
    private Map<String,Object> aiDemandPayload(AiScheduleDemand demand) {
        Map<String,Object> payload=new LinkedHashMap<>();
        payload.put("departmentId", demand.departmentId());
        payload.put("workDate", demand.workDate());
        payload.put("period", demand.period());
        payload.put("expectedVisits", demand.expectedVisits());
        payload.put("riskLevel", demand.riskLevel()==null?"MEDIUM":demand.riskLevel());
        payload.put("historicalVisits", demand.historicalVisits());
        return payload;
    }
    private AiScheduleResponse mapAiScheduleResponse(Map<String,Object> response) {
        if(response==null) return new AiScheduleResponse(null,List.of(),null,null,false,List.of());
        List<AiScheduleSuggestion> suggestions=new ArrayList<>();
        Object rawSuggestions=response.get("suggestions");
        if(rawSuggestions instanceof List<?> items) {
            for(Object rawItem:items) {
                if(rawItem instanceof Map<?,?> item) {
                    suggestions.add(new AiScheduleSuggestion(
                            stringValue(item.get("suggestionId")),
                            stringValue(item.get("doctorId")),
                            stringValue(item.get("doctorName")),
                            stringValue(item.get("departmentId")),
                            stringValue(item.get("workDate")),
                            stringValue(item.get("period")),
                            intValue(item.get("capacity"),20),
                            stringValue(item.get("reason")),
                            booleanValue(item.get("requiresAdminConfirmation"),true)));
                }
            }
        }
        List<Map<String,Object>> knowledgeSources=new ArrayList<>();
        Object rawSources=response.get("knowledgeSources");
        if(rawSources instanceof List<?> items) {
            for(Object rawItem:items) {
                if(rawItem instanceof Map<?,?> item) {
                    Map<String,Object> source=new LinkedHashMap<>();
                    item.forEach((key,value) -> source.put(String.valueOf(key),value));
                    knowledgeSources.add(source);
                }
            }
        }
        return new AiScheduleResponse(
                stringValue(response.get("aiRecordId")),
                suggestions,
                stringValue(response.get("provider")),
                stringValue(response.get("model")),
                booleanValue(response.get("fallbackUsed"),false),
                knowledgeSources);
    }
    private String stringValue(Object value) {
        return value==null?null:String.valueOf(value);
    }
    private int intValue(Object value,int defaultValue) {
        if(value instanceof Number number) return number.intValue();
        if(value==null) return defaultValue;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch(NumberFormatException exception) {
            return defaultValue;
        }
    }
    private boolean booleanValue(Object value,boolean defaultValue) {
        if(value instanceof Boolean bool) return bool;
        if(value==null) return defaultValue;
        return Boolean.parseBoolean(String.valueOf(value));
    }
    private int objectListSize(Object value) {
        return value instanceof List<?> items?items.size():0;
    }
    private List<DoctorCatalogRepository.Department> departmentsNeedingReplan(List<DoctorCatalogRepository.Department> departments,LocalDate start,LocalDate end,
            List<DoctorCatalogRepository.DoctorEvent> events) {
        List<DoctorCatalogRepository.Department> result=new ArrayList<>();
        for(DoctorCatalogRepository.Department department:departments) {
            List<DoctorCatalogRepository.Schedule> schedules=repository.schedules(null,department.id(),start,end).stream()
                    .filter(schedule -> "PUBLISHED".equals(schedule.status()))
                    .toList();
            boolean needed=false;
            for(LocalDate date=start;!date.isAfter(end);date=date.plusDays(1)) {
                if(!coversPeriod(schedules,date,"上午")) {
                    log.info("AI schedule replan needed: missing schedule coverage, department={}, date={}, period=上午",department.id(),date);
                    needed=true;
                }
                if(!coversPeriod(schedules,date,"下午")) {
                    log.info("AI schedule replan needed: missing schedule coverage, department={}, date={}, period=下午",department.id(),date);
                    needed=true;
                }
            }
            if(hasEventConflict(schedules,events)) {
                log.info("AI schedule replan needed: published schedule conflicts with doctor leave/surgery, department={}",department.id());
                needed=true;
            }
            if(needed) {
                result.add(department);
            }
        }
        return result;
    }
    private boolean coversPeriod(List<DoctorCatalogRepository.Schedule> schedules,LocalDate date,String period) {
        return schedules.stream().anyMatch(schedule -> date.equals(schedule.workDate())
                && (period.equals(schedule.period())||"全天".equals(schedule.period())));
    }
    private boolean hasEventConflict(List<DoctorCatalogRepository.Schedule> schedules,List<DoctorCatalogRepository.DoctorEvent> events) {
        return schedules.stream().anyMatch(schedule -> events.stream()
                .filter(event -> event.doctorId().equals(schedule.doctorId()))
                .filter(event -> event.dates().contains(schedule.workDate()))
                .anyMatch(event -> event.periods().stream().anyMatch(period ->
                        period.equals(schedule.period())||"全天".equals(schedule.period())||"全天".equals(period))));
    }
    private AiScheduleRequest normalizeAiScheduleRequest(AiScheduleRequest request) {
        if(request==null) return new AiScheduleRequest(List.of(),List.of());
        List<AiDoctorCandidate> candidates=Optional.ofNullable(request.candidates()).orElse(List.of()).stream()
                .filter(Objects::nonNull)
                .map(candidate -> new AiDoctorCandidate(
                        candidate.doctorId(),candidate.doctorName(),candidate.departmentId(),
                        candidate.specialty()==null?"":candidate.specialty(),
                        candidate.weeklyCapacity(),
                        Optional.ofNullable(candidate.leaveDates()).orElse(List.of()),
                        Optional.ofNullable(candidate.surgeryDates()).orElse(List.of()),
                        Optional.ofNullable(candidate.unavailableSlots()).orElse(List.of())))
                .toList();
        List<AiScheduleDemand> demands=Optional.ofNullable(request.demands()).orElse(List.of()).stream()
                .filter(Objects::nonNull)
                .filter(demand -> List.of("上午","下午","全天").contains(demand.period()))
                .toList();
        return new AiScheduleRequest(candidates,demands);
    }
    private AiScheduleResponse fallbackAiSuggestions(AiScheduleRequest request, String notice) {
        List<AiScheduleSuggestion> suggestions=new ArrayList<>();
        Map<String,Integer> assignedCounts=new HashMap<>();
        Set<String> assignedDoctorDates=new HashSet<>();
        for(AiScheduleDemand demand:request.demands()) {
            List<AiDoctorCandidate> available=request.candidates().stream()
                    .filter(candidate -> demand.departmentId().equals(candidate.departmentId()))
                    .filter(candidate -> !Optional.ofNullable(candidate.leaveDates()).orElse(List.of()).contains(demand.workDate()))
                    .filter(candidate -> !Optional.ofNullable(candidate.surgeryDates()).orElse(List.of()).contains(demand.workDate()))
                    .filter(candidate -> !assignedDoctorDates.contains(candidate.doctorId()+":"+demand.workDate()))
                    .filter(candidate -> !hasUnavailableSlot(candidate,demand.workDate(),demand.period()))
                    .sorted(Comparator
                            .comparingInt((AiDoctorCandidate candidate) -> assignedCounts.getOrDefault(candidate.doctorId(),0))
                            .thenComparing(Comparator.comparingInt(AiDoctorCandidate::weeklyCapacity).reversed())
                            .thenComparing(AiDoctorCandidate::doctorName))
                    .toList();
            if(available.isEmpty()) continue;
            AiDoctorCandidate selected=available.get(0);
            assignedCounts.merge(selected.doctorId(),1,Integer::sum);
            assignedDoctorDates.add(selected.doctorId()+":"+demand.workDate());
            int baseline=demand.historicalVisits()==null?demand.expectedVisits():demand.historicalVisits();
            int capacity=Math.max(8,Math.min(60,Math.round(Math.max(demand.expectedVisits(),baseline)*1.15f)));
            suggestions.add(new AiScheduleSuggestion("local-ai-schedule-"+UUID.randomUUID(),
                    selected.doctorId(),selected.doctorName(),selected.departmentId(),demand.workDate(),demand.period(),capacity,
                    notice+"；结合预计挂号量 "+demand.expectedVisits()+"、风险等级 "+demand.riskLevel()+"、医生可用性和均衡分配生成。",
                    true));
        }
        return new AiScheduleResponse("local-ai-schedule-record-"+UUID.randomUUID(),suggestions,"backend","local-balanced",true,List.of());
    }
    private boolean hasUnavailableSlot(AiDoctorCandidate candidate,String workDate,String period) {
        return Optional.ofNullable(candidate.unavailableSlots()).orElse(List.of()).stream()
                .anyMatch(slot -> workDate.equals(slot.date())
                        && (period.equals(slot.period())||"全天".equals(slot.period())||"全天".equals(period)));
    }
    private int expectedVisits(LocalDate date,String period,int baseVisits,boolean weekendPeak,int weekendIncrease,boolean morningPeak,int morningIncrease) {
        float expected=baseVisits;
        if("下午".equals(period)) expected*=0.75f;
        boolean weekend=date.getDayOfWeek()==java.time.DayOfWeek.SATURDAY||date.getDayOfWeek()==java.time.DayOfWeek.SUNDAY;
        if(weekendPeak&&weekend) expected*=1+weekendIncrease/100f;
        if(morningPeak&&"上午".equals(period)) expected*=1+morningIncrease/100f;
        return Math.max(1,Math.round(expected));
    }
    public record CreateScheduleRequest(String doctorId,String departmentId,String workDate,String period,int capacity) {}
    public record SuspendRequest(String reason) {}
    public record RescheduleRequest(String workDate,String period) {}
    public record ScheduleDto(String id,String doctorId,String doctorName,String departmentId,String workDate,String period,int capacity,int booked,int locked,int available,String status,List<TimeSlotDto> timeSlots) {}
    public record TimeSlotDto(String id,String startTime,int capacity,int booked,int locked,int available) {}
    public record SlotDto(@JsonAlias("slotId") String scheduleId,int capacity,int locked,int booked,int available) {}
    public record AiScheduleRequest(List<AiDoctorCandidate> candidates,List<AiScheduleDemand> demands) {}
    public record AiDoctorCandidate(String doctorId,String doctorName,String departmentId,String specialty,int weeklyCapacity,List<String> leaveDates,List<String> surgeryDates,List<DoctorUnavailableSlot> unavailableSlots) {}
    public record DoctorUnavailableSlot(String date,String period,String type) {}
    public record AiScheduleDemand(String departmentId,String workDate,String period,int expectedVisits,String riskLevel,Integer historicalVisits) {}
    public record AiScheduleResponse(String aiRecordId,List<AiScheduleSuggestion> suggestions,String provider,String model,boolean fallbackUsed,List<Map<String,Object>> knowledgeSources) {}
    public record AiScheduleSuggestion(String suggestionId,String doctorId,String doctorName,String departmentId,String workDate,String period,int capacity,String reason,boolean requiresAdminConfirmation) {}
    public record PublishAiScheduleRequest(String aiRecordId,String doctorId,String departmentId,String workDate,String period,int capacity,String reason) {}
    public record PublishAiScheduleBatchRequest(String aiRecordId,List<PublishAiScheduleRequest> suggestions) {}
}
