package com.cloudbrain.doctor.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/doctors")
public class DoctorController {
    private static final List<DoctorDto> DOCTORS = List.of(
            new DoctorDto("doctor-001", "张医生", "主任医师", "dept-neuro", "神经内科", "头痛与脑血管疾病"),
            new DoctorDto("doctor-002", "李医生", "副主任医师", "dept-imaging", "影像科", "头部 CT/MRI 影像诊断"),
            new DoctorDto("doctor-003", "陈医生", "主治医师", "dept-general", "全科医学", "慢病管理"));

    @GetMapping
    public List<DoctorDto> list(@RequestParam(name = "departmentId", required = false) String departmentId) {
        if (departmentId == null || departmentId.isBlank()) {
            return DOCTORS;
        }
        return DOCTORS.stream().filter(doctor -> departmentId.equals(doctor.departmentId())).toList();
    }

    public record DoctorDto(
            String id,
            String name,
            String title,
            String departmentId,
            String departmentName,
            String specialty) {
    }
}
