package com.cloudbrain.doctor.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {
    @GetMapping
    public List<DepartmentDto> list() {
        return List.of(
                new DepartmentDto("dept-neuro", "神经内科", "头痛、眩晕、脑血管疾病"),
                new DepartmentDto("dept-imaging", "影像科", "CT/MRI 检查与影像报告"),
                new DepartmentDto("dept-general", "全科医学", "常见病与慢病复诊"));
    }

    public record DepartmentDto(String id, String name, String description) {
    }
}

