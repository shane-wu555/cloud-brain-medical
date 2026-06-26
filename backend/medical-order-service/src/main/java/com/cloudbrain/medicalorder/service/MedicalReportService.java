package com.cloudbrain.medicalorder.service;

import com.cloudbrain.medicalorder.domain.AiMedicalTask;
import com.cloudbrain.medicalorder.domain.MedicalAttachment;
import com.cloudbrain.medicalorder.domain.MedicalOrder;
import com.cloudbrain.medicalorder.domain.MedicalReport;
import com.cloudbrain.medicalorder.repository.MedicalOrderRepository;
import com.cloudbrain.medicalorder.repository.MedicalReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MedicalReportService {
    private final MedicalReportRepository reports;
    private final MedicalOrderRepository orders;
    private final MedicalOrderService orderService;
    private final MinioStorageService storage;
    private final AiCtClient ai;
    private final ObjectMapper mapper;
    private final ClinicalWorkflowClient workflow;
    private final PatientAccessClient patientAccessClient;

    public MedicalReportService(
            MedicalReportRepository reports,
            MedicalOrderRepository orders,
            MedicalOrderService orderService,
            MinioStorageService storage,
            AiCtClient ai,
            ObjectMapper mapper,
            ClinicalWorkflowClient workflow,
            PatientAccessClient patientAccessClient) {
        this.reports = reports;
        this.orders = orders;
        this.orderService = orderService;
        this.storage = storage;
        this.ai = ai;
        this.mapper = mapper;
        this.workflow = workflow;
        this.patientAccessClient = patientAccessClient;
    }

    public MedicalAttachment upload(String orderId, MultipartFile file, String actor) {
        MedicalOrder order = order(orderId);
        checkExecutor(order, actor);
        if (!"CHECK".equals(order.orderType())) throw new IllegalArgumentException("仅检查医嘱支持影像附件");
        try {
            String key = "orders/" + orderId + "/" + UUID.randomUUID() + "-" + file.getOriginalFilename();
            String bucket = storage.put(key, file.getInputStream(), file.getSize(), file.getContentType());
            return reports.attachment(orderId, key, file.getOriginalFilename(), file.getContentType(), file.getSize(), bucket, actor);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("读取上传文件失败", e);
        }
    }

    public AiMedicalTask submitCt(String orderId, String attachmentId, String actor) {
        MedicalOrder order = order(orderId);
        checkExecutor(order, actor);
        MedicalAttachment attachment = reports.attachments(orderId).stream()
                .filter(a -> a.id().equals(attachmentId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("影像附件不存在"));
        Map<String, Object> response = ai.submit(orderId, attachment.objectKey());
        return reports.createTask(orderId, (String) response.get("taskId"));
    }

    @SuppressWarnings("unchecked")
    public AiMedicalTask refresh(String externalId, String actor) {
        AiMedicalTask task = reports.taskByExternal(externalId).orElseThrow(() -> new IllegalArgumentException("AI 任务不存在"));
        checkExecutor(order(task.medicalOrderId()), actor);
        Map<String, Object> result = ai.task(externalId);
        String status = (String) result.get("status");
        String output;
        try {
            output = mapper.writeValueAsString(result.getOrDefault("result", Map.of()));
        } catch (Exception e) {
            output = "{}";
        }
        AiMedicalTask updated = reports.updateTask(externalId, status, (String) result.get("modelVersion"), output, (String) result.get("error"));
        if ("COMPLETED".equals(status) && reports.reportByOrder(task.medicalOrderId()).isEmpty()) {
            Map<String, Object> data = (Map<String, Object>) result.getOrDefault("result", Map.of());
            reports.saveDraft(task.medicalOrderId(), "CHECK", (String) data.getOrDefault("findings", ""),
                    (String) data.getOrDefault("conclusion", ""), (String) data.getOrDefault("riskAdvice", ""),
                    "AI", updated.id());
        }
        return updated;
    }

    public MedicalReport manualDraft(String orderId, String findings, String conclusion, String advice, String actor) {
        MedicalOrder order = order(orderId);
        checkExecutor(order, actor);
        return reports.saveDraft(orderId, order.orderType(), findings, conclusion, advice, "HUMAN", null);
    }

    @Transactional
    public MedicalReport confirm(String orderId, String findings, String conclusion, String advice, String actor, String role) {
        MedicalOrder order = order(orderId);
        validateRole(order, role);
        checkExecutor(order, actor);
        MedicalReport draft = reports.reportByOrder(orderId).orElseThrow(() -> new IllegalArgumentException("请先生成或保存报告草稿"));
        String finalFindings = blank(findings) ? draft.findings() : findings;
        String finalConclusion = blank(conclusion) ? draft.conclusion() : conclusion;
        String finalAdvice = blank(advice) ? draft.advice() : advice;
        if (blank(finalConclusion)) throw new IllegalArgumentException("报告结论不能为空");
        MedicalReport report = reports.confirm(orderId, finalFindings, finalConclusion, finalAdvice, actor);
        String source = report.createdByType();
        orderService.complete(orderId, actor, role, "{}", finalConclusion, source, "AI".equals(source) ? report.aiTaskId() : null);
        workflow.publish(order, report);
        return report;
    }

    public MedicalReport reject(String orderId, String reason, String actor, String role) {
        MedicalOrder order = order(orderId);
        validateRole(order, role);
        checkExecutor(order, actor);
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("驳回原因不能为空");
        return reports.reject(orderId, actor, reason);
    }

    public List<MedicalReport> list(String patientId, String actor, String role) {
        String scopedPatientId = patientId;
        if ("PATIENT".equals(role)) {
            if (scopedPatientId == null || scopedPatientId.isBlank()) scopedPatientId = patientAccessClient.boundPatientId(actor);
            if (scopedPatientId == null || scopedPatientId.isBlank()) throw new AccessDeniedException("请先添加并绑定就诊人");
            if (!patientAccessClient.owns(actor, scopedPatientId)) throw new AccessDeniedException("无权查看该就诊人的报告");
        }
        String finalPatientId = scopedPatientId;
        return reports.reports().stream().filter(r -> {
            if ("DISPOSAL".equals(r.reportType())) return false;
            MedicalOrder o = order(r.medicalOrderId());
            if ("PATIENT".equals(role)) return "CONFIRMED".equals(r.status()) && o.patientId().equals(finalPatientId);
            if ("OUTPATIENT_DOCTOR".equals(role)) return "CONFIRMED".equals(r.status()) && o.orderingDoctorId().equals(actor);
            if (Set.of("CHECK_DOCTOR", "LAB_DOCTOR", "DISPOSAL_DOCTOR").contains(role)) return actor.equals(o.executorId());
            return false;
        }).toList();
    }

    public List<MedicalAttachment> attachments(String orderId, String actor, String role) {
        MedicalOrder o = order(orderId);
        boolean allowed = ("PATIENT".equals(role) && patientAccessClient.owns(actor, o.patientId()))
                || ("OUTPATIENT_DOCTOR".equals(role) && o.orderingDoctorId().equals(actor))
                || (Set.of("CHECK_DOCTOR", "LAB_DOCTOR", "DISPOSAL_DOCTOR").contains(role) && actor.equals(o.executorId()));
        if (!allowed) throw new AccessDeniedException("无权查看附件");
        return reports.attachments(orderId);
    }

    private MedicalOrder order(String id) {
        return orders.findById(id).orElseThrow(() -> new IllegalArgumentException("医技申请不存在"));
    }

    private void checkExecutor(MedicalOrder o, String actor) {
        if (!actor.equals(o.executorId())) throw new AccessDeniedException("只能处理分配给自己的医嘱");
    }

    private void validateRole(MedicalOrder o, String role) {
        String expected = Map.of("CHECK", "CHECK_DOCTOR", "LAB", "LAB_DOCTOR", "DISPOSAL", "DISPOSAL_DOCTOR").get(o.orderType());
        if (!expected.equals(role)) throw new AccessDeniedException("角色与报告类型不匹配");
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
