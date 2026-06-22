package com.cloudbrain.doctor.controller;

import com.cloudbrain.doctor.repository.DoctorCatalogRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/doctors")
public class DoctorController {
    private final DoctorCatalogRepository repository;
    public DoctorController(DoctorCatalogRepository repository){this.repository=repository;}

    @GetMapping
    public List<DoctorDto> list(@RequestParam(name = "departmentId", required = false) String departmentId) {
        return repository.doctors(departmentId).stream().map(d->new DoctorDto(d.id(),d.name(),d.title(),d.departmentId(),d.departmentName(),d.specialty(),d.roleType())).toList();
    }

    public record DoctorDto(
            String id,
            String name,
            String title,
            String departmentId,
            String departmentName,
            String specialty,
            String roleType) {
    }
}
