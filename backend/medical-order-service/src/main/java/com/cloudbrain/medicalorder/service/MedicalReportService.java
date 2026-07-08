package com.cloudbrain.medicalorder.service;

import com.cloudbrain.medicalorder.audit.AuditPublisher;
import com.cloudbrain.medicalorder.domain.AiMedicalTask;
import com.cloudbrain.medicalorder.domain.MedicalAttachment;
import com.cloudbrain.medicalorder.domain.MedicalOrder;
import com.cloudbrain.medicalorder.domain.MedicalReport;
import com.cloudbrain.medicalorder.repository.MedicalOrderRepository;
import com.cloudbrain.medicalorder.repository.MedicalReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
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
    private final AuditPublisher auditPublisher;
    private final long ctTaskTimeoutSeconds;

    public MedicalReportService(
            MedicalReportRepository reports,
            MedicalOrderRepository orders,
            MedicalOrderService orderService,
            MinioStorageService storage,
            AiCtClient ai,
            ObjectMapper mapper,
            ClinicalWorkflowClient workflow,
            PatientAccessClient patientAccessClient,
            AuditPublisher auditPublisher,
            @Value("${ai.ct.task-timeout-seconds:360}") long ctTaskTimeoutSeconds) {
        this.reports = reports;
        this.orders = orders;
        this.orderService = orderService;
        this.storage = storage;
        this.ai = ai;
        this.mapper = mapper;
        this.workflow = workflow;
        this.patientAccessClient = patientAccessClient;
        this.auditPublisher = auditPublisher;
        this.ctTaskTimeoutSeconds = ctTaskTimeoutSeconds;
    }

    public MedicalAttachment upload(String orderId, MultipartFile file, String actor) {
        MedicalOrder order = order(orderId);
        checkExecutor(order, actor);
        if (!Set.of("CHECK", "LAB").contains(order.orderType())) {
            throw new IllegalArgumentException("Attachments are only supported for check and lab orders");
        }
        try {
            String key = "orders/" + orderId + "/" + UUID.randomUUID() + "-" + file.getOriginalFilename();
            String bucket = storage.put(key, file.getInputStream(), file.getSize(), file.getContentType());
            return reports.attachment(orderId, key, file.getOriginalFilename(), file.getContentType(), file.getSize(), bucket, actor);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Failed to read uploaded file", error);
        }
    }

    public AiMedicalTask submitCt(String orderId, String attachmentId, String actor) {
        MedicalOrder order = order(orderId);
        checkExecutor(order, actor);
        if (!"CHECK".equals(order.orderType())) {
            throw new IllegalArgumentException("CT analysis is only supported for check orders");
        }
        MedicalAttachment attachment = reports.attachments(orderId).stream()
                .filter(candidate -> candidate.id().equals(attachmentId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Attachment does not exist"));
        Map<String, Object> response = ai.submit(orderId, attachment.objectKey(), order.bodyPart(), order.purpose());
        return reports.createTask(orderId, (String) response.get("taskId"));
    }

    @SuppressWarnings("unchecked")
    public AiMedicalTask refresh(String externalId, String actor) {
        AiMedicalTask task = reports.taskByExternal(externalId)
                .orElseThrow(() -> new IllegalArgumentException("AI task does not exist"));
        checkExecutor(order(task.medicalOrderId()), actor);
        Optional<AiMedicalTask> timedOut = reports.timeoutTaskIfExpired(externalId, ctTaskTimeoutSeconds);
        if (timedOut.isPresent()) {
            return timedOut.get();
        }
        Map<String, Object> result = ai.task(externalId);
        String status = (String) result.get("status");
        String output;
        try {
            output = mapper.writeValueAsString(result.getOrDefault("result", Map.of()));
        } catch (Exception error) {
            output = "{}";
        }
        AiMedicalTask updated = reports.updateTask(
                externalId,
                status,
                (String) result.get("modelVersion"),
                output,
                (String) result.get("error"));
        if ("COMPLETED".equals(status) && reports.reportByOrder(task.medicalOrderId()).isEmpty()) {
            Map<String, Object> data = (Map<String, Object>) result.getOrDefault("result", Map.of());
            reports.saveDraft(
                    task.medicalOrderId(),
                    "CHECK",
                    (String) data.getOrDefault("findings", ""),
                    (String) data.getOrDefault("conclusion", ""),
                    (String) data.getOrDefault("riskAdvice", ""),
                    "AI",
                    updated.id());
        }
        return updated;
    }

    public MedicalReport manualDraft(String orderId, String findings, String conclusion, String advice, String actor) {
        MedicalOrder order = order(orderId);
        checkExecutor(order, actor);
        return reports.saveDraft(orderId, order.orderType(), findings, conclusion, advice, "HUMAN", null);
    }

    @Transactional
    public MedicalReport confirm(
            String orderId,
            String findings,
            String conclusion,
            String advice,
            String actor,
            String role) {
        MedicalOrder order = order(orderId);
        validateRole(order, role);
        checkExecutor(order, actor);
        MedicalReport draft = reports.reportByOrder(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Draft report does not exist"));
        String finalFindings = blank(findings) ? draft.findings() : findings;
        String finalConclusion = blank(conclusion) ? draft.conclusion() : conclusion;
        String finalAdvice = blank(advice) ? draft.advice() : advice;
        if (blank(finalConclusion)) {
            throw new IllegalArgumentException("Report conclusion cannot be blank");
        }

        MedicalOrder orderForPublish = order;
        if (!"COMPLETED".equals(order.status()) && Set.of("WAITING", "CALLED").contains(order.status())) {
            orderForPublish = orderService.start(orderId, actor, role);
        }

        MedicalReport report = reports.confirm(orderId, finalFindings, finalConclusion, finalAdvice, actor);
        String source = report.createdByType();
        if (!"COMPLETED".equals(orderForPublish.status())) {
            orderForPublish = orderService.complete(
                    orderId,
                    actor,
                    role,
                    finalConclusion,
                    source,
                    "AI".equals(source) ? report.aiTaskId() : null);
        }
        workflow.publish(orderForPublish, report);
        auditPublisher.publish(
                "MEDICAL_REPORT_CONFIRM",
                "MEDICAL_REPORT",
                report.id(),
                orderForPublish.patientId(),
                orderForPublish.id(),
                actor,
                role,
                Map.of(
                        "reportType", report.reportType(),
                        "source", source,
                        "status", report.status()));
        if ("AI".equals(source)) {
            auditPublisher.publish(
                    "AI_RESULT_CONFIRMED",
                    "MEDICAL_REPORT",
                    report.id(),
                    orderForPublish.patientId(),
                    orderForPublish.id(),
                    actor,
                    role,
                    Map.of(
                            "aiTaskId", report.aiTaskId(),
                            "adoptionStatus", report.modifiedFromAi() ? "MODIFIED" : "ADOPTED",
                            "reportType", report.reportType()));
        }
        return report;
    }

    public MedicalReport reject(String orderId, String reason, String actor, String role) {
        MedicalOrder order = order(orderId);
        validateRole(order, role);
        checkExecutor(order, actor);
        if (blank(reason)) {
            throw new IllegalArgumentException("Rejection reason cannot be blank");
        }
        MedicalReport report = reports.reject(orderId, actor, reason);
        auditPublisher.publish(
                "MEDICAL_REPORT_REJECT",
                "MEDICAL_REPORT",
                report.id(),
                order.patientId(),
                order.id(),
                actor,
                role,
                Map.of("reason", reason, "reportType", report.reportType()));
        return report;
    }

    public List<MedicalReport> list(String patientId, String actor, String role) {
        String scopedPatientId = patientId;
        if ("PATIENT".equals(role)) {
            if (blank(scopedPatientId)) {
                scopedPatientId = patientAccessClient.boundPatientId(actor);
            }
            if (blank(scopedPatientId)) {
                throw new AccessDeniedException("Patient account is not bound to a patient profile");
            }
            if (!patientAccessClient.owns(actor, scopedPatientId)) {
                throw new AccessDeniedException("Cannot access another patient's reports");
            }
        }

        String finalPatientId = scopedPatientId;
        List<MedicalReport> visible = reports.reports().stream().filter(report -> {
            MedicalOrder medicalOrder = order(report.medicalOrderId());
            if ("PATIENT".equals(role)) {
                return "CONFIRMED".equals(report.status()) && medicalOrder.patientId().equals(finalPatientId);
            }
            if ("OUTPATIENT_DOCTOR".equals(role)) {
                return "CONFIRMED".equals(report.status()) && medicalOrder.orderingDoctorId().equals(actor);
            }
            if (Set.of("CHECK_DOCTOR", "LAB_DOCTOR", "DISPOSAL_DOCTOR").contains(role)) {
                return canAccessWorkspace(medicalOrder, actor);
            }
            return false;
        }).toList();

        auditPublisher.publish(
                "MEDICAL_REPORT_LIST_VIEW",
                "MEDICAL_REPORT",
                null,
                finalPatientId,
                null,
                actor,
                role,
                Map.of(
                        "accessScope", "LIST",
                        "resultCount", visible.size()));
        return visible;
    }

    public List<MedicalAttachment> attachments(String orderId, String actor, String role) {
        MedicalOrder order = order(orderId);
        boolean allowed = ("PATIENT".equals(role) && patientAccessClient.owns(actor, order.patientId()))
                || ("OUTPATIENT_DOCTOR".equals(role) && order.orderingDoctorId().equals(actor))
                || (Set.of("CHECK_DOCTOR", "LAB_DOCTOR", "DISPOSAL_DOCTOR").contains(role) && canAccessWorkspace(order, actor));
        if (!allowed) {
            throw new AccessDeniedException("Cannot access attachment");
        }
        return reports.attachments(orderId);
    }

    public AttachmentDownload attachmentContent(String orderId, String attachmentId, String actor, String role) {
        MedicalOrder medicalOrder = order(orderId);
        MedicalAttachment attachment = attachments(orderId, actor, role).stream()
                .filter(candidate -> candidate.id().equals(attachmentId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Attachment does not exist"));
        auditPublisher.publish(
                "MEDICAL_ATTACHMENT_DOWNLOAD",
                "MEDICAL_ATTACHMENT",
                attachment.id(),
                medicalOrder.patientId(),
                medicalOrder.id(),
                actor,
                role,
                Map.of(
                        "contentType", attachment.contentType() == null ? "" : attachment.contentType(),
                        "originalName", attachment.originalName() == null ? "" : attachment.originalName()));
        return new AttachmentDownload(attachment, storage.get(attachment.objectKey()));
    }

    private MedicalOrder order(String id) {
        return orders.findById(id).orElseThrow(() -> new IllegalArgumentException("Medical order does not exist"));
    }

    private void checkExecutor(MedicalOrder order, String actor) {
        if (!canAccessWorkspace(order, actor)) {
            throw new AccessDeniedException("Actor cannot access this medical order workspace");
        }
    }

    private boolean canAccessWorkspace(MedicalOrder order, String actor) {
        return orders.staffRoom(actor)
                .map(staffRoom -> staffRoom.roomId().equals(order.roomId()))
                .orElse(false);
    }

    private void validateRole(MedicalOrder order, String role) {
        String expectedRole = Map.of(
                "CHECK", "CHECK_DOCTOR",
                "LAB", "LAB_DOCTOR",
                "DISPOSAL", "DISPOSAL_DOCTOR").get(order.orderType());
        if (!expectedRole.equals(role)) {
            throw new AccessDeniedException("Role does not match medical report type");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record AttachmentDownload(MedicalAttachment attachment, java.io.InputStream stream) {
    }
}
