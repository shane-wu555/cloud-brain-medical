package com.cloudbrain.doctor.controller;

import com.cloudbrain.doctor.repository.DoctorCatalogRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
public class CatalogSearchController {
    private final DoctorCatalogRepository repository;

    public CatalogSearchController(DoctorCatalogRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/patient-search")
    public PatientSearchDto patientSearch(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        DoctorCatalogRepository.PatientSearchResult result = repository.patientSearch(keyword, limit);
        return new PatientSearchDto(
                result.departments().stream().map(this::departmentDto).toList(),
                result.doctors().stream().map(this::doctorDto).toList());
    }

    private DepartmentDto departmentDto(DoctorCatalogRepository.Department department) {
        return new DepartmentDto(department.id(), department.name(), department.description());
    }

    private DoctorDto doctorDto(DoctorCatalogRepository.Doctor doctor) {
        return new DoctorDto(doctor.id(), doctor.employeeNo(), doctor.name(), doctor.title(),
                doctor.departmentId(), doctor.departmentName(), doctor.specialty());
    }

    public record PatientSearchDto(List<DepartmentDto> departments, List<DoctorDto> doctors) {}
    public record DepartmentDto(String id, String name, String description) {}
    public record DoctorDto(String id, String employeeNo, String name, String title,
            String departmentId, String departmentName, String specialty) {}
}
