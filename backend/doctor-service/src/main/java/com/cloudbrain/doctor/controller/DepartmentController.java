package com.cloudbrain.doctor.controller;

import com.cloudbrain.doctor.repository.DoctorCatalogRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/departments")
public class DepartmentController {
    private final DoctorCatalogRepository repository;
    public DepartmentController(DoctorCatalogRepository repository) { this.repository = repository; }
    @GetMapping public List<DepartmentDto> list() { return repository.departments().stream().map(this::dto).toList(); }
    @PostMapping @PreAuthorize("hasRole('ADMIN')")
    public DepartmentDto create(@RequestBody CreateDepartmentRequest request) {
        return dto(repository.createDepartment(request.name(), request.description()));
    }
    private DepartmentDto dto(DoctorCatalogRepository.Department d) { return new DepartmentDto(d.id(),d.name(),d.description()); }
    public record DepartmentDto(String id,String name,String description) {}
    public record CreateDepartmentRequest(String name,String description) {}
}
