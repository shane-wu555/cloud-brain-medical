package com.cloudbrain.patient.controller;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patients")
public class PatientController {
    @GetMapping("/me")
    public Map<String, Object> me() {
        return Map.of(
                "id", "patient-001",
                "name", "王小云",
                "gender", "女",
                "age", 32,
                "phone", "13800000000",
                "tags", List.of("高血压随访", "头痛复诊"));
    }
}

