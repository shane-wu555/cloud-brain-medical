package com.cloudbrain.appointment.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/consultations")
public class AiConsultationController {
    @PostMapping
    public Map<String, Object> consult(@RequestBody ConsultationRequest request) {
        List<String> tags = request.symptomTags() == null ? List.of() : request.symptomTags();
        String text = (request.description() == null ? "" : request.description()).toLowerCase(Locale.ROOT);
        boolean neuro = tags.contains("头痛") || tags.contains("眩晕") || text.contains("头痛") || text.contains("眩晕");
        boolean imaging = tags.contains("影像复查") || text.contains("ct") || text.contains("mri");

        String departmentId = imaging ? "dept-imaging" : neuro ? "dept-neuro" : "dept-general";
        String departmentName = imaging ? "影像科" : neuro ? "神经内科" : "全科医学";
        String riskLevel = tags.contains("剧烈疼痛") || text.contains("昏迷") ? "HIGH" : neuro ? "MEDIUM" : "LOW";
        List<Map<String, String>> doctors = new ArrayList<>();
        if ("dept-neuro".equals(departmentId)) {
            doctors.add(Map.of("doctorId", "doctor-001", "doctorName", "张医生", "reason", "擅长头痛、眩晕与脑血管疾病"));
        }
        if ("dept-imaging".equals(departmentId)) {
            doctors.add(Map.of("doctorId", "doctor-002", "doctorName", "李医生", "reason", "擅长头部 CT/MRI 影像诊断"));
        }
        doctors.add(Map.of("doctorId", "doctor-003", "doctorName", "陈医生", "reason", "适合常见病和慢病复诊"));

        return Map.of(
                "summary", "症状摘要：" + request.description(),
                "riskLevel", riskLevel,
                "recommendedDepartmentId", departmentId,
                "recommendedDepartmentName", departmentName,
                "recommendedDoctors", doctors.stream().limit(5).toList(),
                "suggestOfflineUrgent", "HIGH".equals(riskLevel),
                "recordDraft", "AI问诊初稿，需由医生接诊时复核：" + request.description());
    }

    public record ConsultationRequest(String patientId, String description, List<String> symptomTags) {
    }
}

