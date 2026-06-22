package com.cloudbrain.doctor.controller;

import com.cloudbrain.doctor.repository.DoctorCatalogRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {
    private final DoctorCatalogRepository repository;
    public DepartmentController(DoctorCatalogRepository repository){this.repository=repository;}
    @GetMapping
    public List<DepartmentDto> list() {
        return repository.departments().stream().map(d->new DepartmentDto(d.id(),d.name(),d.description())).toList();
    }

    public record DepartmentDto(String id, String name, String description) {
    }
}
