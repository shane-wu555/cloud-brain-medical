package com.cloudbrain.doctor.controller;

import com.cloudbrain.doctor.repository.DoctorCatalogRepository;
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
        return dto(repository.createDoctor(request.name(),request.title(),request.departmentId(),request.roleType(),request.specialty()));
    }
    private DoctorDto dto(DoctorCatalogRepository.Doctor d) {
        return new DoctorDto(d.id(),d.name(),d.title(),d.departmentId(),d.departmentName(),d.specialty(),d.roleType());
    }
    public record DoctorDto(String id,String name,String title,String departmentId,String departmentName,String specialty,String roleType) {}
    public record CreateDoctorRequest(String name,String title,String departmentId,String roleType,String specialty) {}
}
