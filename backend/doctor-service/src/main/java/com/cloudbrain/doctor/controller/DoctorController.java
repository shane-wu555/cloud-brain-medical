package com.cloudbrain.doctor.controller;

import com.cloudbrain.doctor.repository.DoctorCatalogRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/doctors")
public class DoctorController {
    private final DoctorCatalogRepository repository;
    public DoctorController(DoctorCatalogRepository repository) { this.repository = repository; }
    @GetMapping public List<DoctorDto> list(@RequestParam(name="departmentId", required=false) String departmentId) {
        return repository.doctors(departmentId).stream().map(this::dto).toList();
    }
    @PostMapping @PreAuthorize("hasRole('ADMIN')")
    public DoctorDto create(@RequestBody CreateDoctorRequest request) {
        if (request.employeeNo() == null || request.employeeNo().isBlank()) {
            throw new IllegalArgumentException("工号不能为空");
        }
        String roleType = request.roleType() == null || request.roleType().isBlank() ? "OUTPATIENT_DOCTOR" : request.roleType();
        if (!"OUTPATIENT_DOCTOR".equals(roleType)) {
            throw new IllegalArgumentException("doctor-service 只维护门诊医生档案");
        }
        return dto(repository.createDoctor(request.employeeNo().trim(),request.name(),request.title(),request.departmentId(),roleType,request.specialty()));
    }
    @GetMapping("/{id}")
    public DoctorDto detail(@PathVariable String id) {
        return dto(repository.findDoctor(id));
    }
    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    public DoctorDto update(@PathVariable String id, @RequestBody UpdateDoctorRequest request) {
        return dto(repository.updateDoctor(id, request.name(), request.title(), request.departmentId(), request.specialty()));
    }
    @GetMapping("/events")
    public List<DoctorEventDto> events() {
        return repository.doctorEvents().stream().map(this::eventDto).toList();
    }
    @PostMapping("/events") @PreAuthorize("hasRole('ADMIN')")
    public DoctorEventDto createEvent(@RequestBody DoctorEventRequest request) {
        return eventDto(repository.createDoctorEvent(
                request.doctorId(), request.eventType(), request.dates(), request.periods(), request.note()));
    }
    @PutMapping("/events/{id}") @PreAuthorize("hasRole('ADMIN')")
    public DoctorEventDto updateEvent(@PathVariable String id, @RequestBody DoctorEventRequest request) {
        return eventDto(repository.updateDoctorEvent(
                id, request.doctorId(), request.eventType(), request.dates(), request.periods(), request.note()));
    }
    @DeleteMapping("/events/{id}") @PreAuthorize("hasRole('ADMIN')")
    public void deleteEvent(@PathVariable String id) {
        repository.deleteDoctorEvent(id);
    }
    private DoctorDto dto(DoctorCatalogRepository.Doctor d) {
        return new DoctorDto(d.id(),d.employeeNo(),d.name(),d.title(),d.departmentId(),d.departmentName(),
                d.specialty(),d.roleType(),d.roomId(),d.roomName());
    }
    private DoctorEventDto eventDto(DoctorCatalogRepository.DoctorEvent event) {
        return new DoctorEventDto(event.id(), event.doctorId(), event.doctorName(), event.departmentName(),
                event.eventType(), event.dates(), event.periods(), event.note());
    }
    public record DoctorDto(String id,String employeeNo,String name,String title,String departmentId,String departmentName,
            String specialty,String roleType,String roomId,String roomName) {}
    public record CreateDoctorRequest(String employeeNo,String name,String title,String departmentId,String roleType,String specialty) {}
    public record UpdateDoctorRequest(String name,String title,String departmentId,String specialty) {}
    public record DoctorEventDto(String id,String doctorId,String doctorName,String departmentName,
            String eventType,List<LocalDate> dates,List<String> periods,String note) {}
    public record DoctorEventRequest(String doctorId,String eventType,List<LocalDate> dates,List<String> periods,String note) {}
}
