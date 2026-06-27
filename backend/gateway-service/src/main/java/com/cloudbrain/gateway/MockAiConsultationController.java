package com.cloudbrain.gateway;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MockAiConsultationController {
    private static final List<String> NEURO_KEYWORDS = List.of(
            "头痛", "头晕", "眩晕", "恶心", "呕吐", "肢体", "麻木", "无力", "抽搐", "癫痫", "言语", "昏迷", "意识");
    private static final List<String> URGENT_KEYWORDS = List.of(
            "剧烈头痛", "突然", "昏迷", "意识不清", "抽搐", "偏瘫", "说话不清", "喷射性呕吐", "外伤");

    @PostMapping("/api/ai/consultations")
    public ConsultationResponse createConsultation(@RequestBody ConsultationRequest request) {
        return buildResponse("consult-" + UUID.randomUUID(), request.description(), request.symptomTags());
    }

    @PostMapping("/api/ai/consultations/{consultationId}/messages")
    public ConsultationResponse createConsultationMessage(
            @PathVariable String consultationId,
            @RequestBody ConsultationMessageRequest request) {
        return buildResponse(consultationId, request.message(), request.symptomTags());
    }

    private ConsultationResponse buildResponse(String consultationId, String description, List<String> symptomTags) {
        String text = normalize(description) + " " + String.join(" ", safeList(symptomTags));
        boolean urgent = containsAny(text, URGENT_KEYWORDS);
        boolean neurological = urgent || containsAny(text, NEURO_KEYWORDS);
        String riskLevel = urgent ? "HIGH" : neurological ? "MEDIUM" : "LOW";
        String departmentId = neurological ? "dept-neuro" : "dept-general";
        String departmentName = neurological ? "神经内科" : "全科医学科";
        List<DoctorRecommendation> doctors = neurological
                ? List.of(new DoctorRecommendation("doctor-001", "张医生", "擅长头痛、眩晕、脑血管相关症状的门诊评估"))
                : List.of(new DoctorRecommendation("doctor-003", "陈医生", "适合常见不适和慢病初诊咨询"));
        List<String> followUpQuestions = urgent ? List.of() : followUpQuestions(description);
        String cleanDescription = normalize(description);

        return new ConsultationResponse(
                consultationId,
                "ai-consult-mock-" + UUID.randomUUID(),
                cleanDescription.isBlank() ? "已创建模拟问诊，请继续补充症状。" : "症状摘要：" + cleanDescription,
                riskLevel,
                departmentId,
                departmentName,
                doctors,
                urgent,
                !followUpQuestions.isEmpty(),
                followUpQuestions,
                recordDraft(cleanDescription, riskLevel, departmentName),
                "gateway-mock",
                "mock-consultation-v1",
                true,
                List.of(),
                "当前为本地 mock 问诊结果，仅用于演示和流程联调，不能替代医生诊断；如有急危重症状请立即线下急诊就医。",
                OffsetDateTime.now().toString());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static boolean containsAny(String text, List<String> keywords) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return keywords.stream().anyMatch(normalized::contains);
    }

    private static List<String> followUpQuestions(String description) {
        String text = normalize(description);
        if (text.length() < 12) {
            return List.of(
                    "请补充症状开始时间、持续多久，以及是否突然加重。",
                    "是否伴随头痛、眩晕、恶心呕吐、肢体无力、言语不清或近期外伤？");
        }
        return List.of("是否有既往高血压、糖尿病、脑血管病史，或正在长期服药？");
    }

    private static String recordDraft(String description, String riskLevel, String departmentName) {
        String symptom = description.isBlank() ? "患者症状信息待补充" : description;
        return "AI模拟问诊初稿：主诉「" + symptom + "」。风险分级 " + riskLevel
                + "，建议优先选择" + departmentName + "门诊，由医生进一步评估。";
    }

    public record ConsultationRequest(String patientId, String description, List<String> symptomTags) {
    }

    public record ConsultationMessageRequest(String message, List<String> symptomTags) {
    }

    public record ConsultationResponse(
            String consultationId,
            String aiRecordId,
            String summary,
            String riskLevel,
            String recommendedDepartmentId,
            String recommendedDepartmentName,
            List<DoctorRecommendation> recommendedDoctors,
            boolean suggestOfflineUrgent,
            boolean needsFollowUp,
            List<String> followUpQuestions,
            String recordDraft,
            String provider,
            String model,
            boolean fallbackUsed,
            List<Object> knowledgeSources,
            String safetyNotice,
            String createdAt) {
    }

    public record DoctorRecommendation(String doctorId, String doctorName, String reason) {
    }
}
