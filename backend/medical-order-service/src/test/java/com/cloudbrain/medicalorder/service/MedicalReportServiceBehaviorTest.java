package com.cloudbrain.medicalorder.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.medicalorder.audit.AuditPublisher;
import com.cloudbrain.medicalorder.domain.AiMedicalTask;
import com.cloudbrain.medicalorder.domain.MedicalAttachment;
import com.cloudbrain.medicalorder.domain.MedicalOrder;
import com.cloudbrain.medicalorder.domain.MedicalReport;
import com.cloudbrain.medicalorder.repository.MedicalOrderRepository;
import com.cloudbrain.medicalorder.repository.MedicalReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

class MedicalReportServiceBehaviorTest {
    private final MedicalReportRepository reports = Mockito.mock(MedicalReportRepository.class);
    private final MedicalOrderRepository orders = Mockito.mock(MedicalOrderRepository.class);
    private final MedicalOrderService orderService = Mockito.mock(MedicalOrderService.class);
    private final MinioStorageService storage = Mockito.mock(MinioStorageService.class);
    private final AiCtClient ai = Mockito.mock(AiCtClient.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final ClinicalWorkflowClient workflow = Mockito.mock(ClinicalWorkflowClient.class);
    private final PatientAccessClient patientAccessClient = Mockito.mock(PatientAccessClient.class);
    private final AuditPublisher auditPublisher = Mockito.mock(AuditPublisher.class);
    private final MedicalReportService service = new MedicalReportService(
            reports, orders, orderService, storage, ai, mapper, workflow, patientAccessClient, auditPublisher, 360);

    @Test
    void uploadSupportsCheckAndLabOrdersAndWrapsIoFailure() throws Exception {
        MedicalOrder checkOrder = order("order-1", "CHECK", "patient-1", "doctor-1", "room-1", "WAITING");
        MockMultipartFile file = new MockMultipartFile("file", "ct.png", "image/png", new byte[] {1, 2, 3});
        MedicalAttachment attachment = attachment("attachment-1", "order-1");
        when(orders.findById("order-1")).thenReturn(Optional.of(checkOrder));
        when(orders.staffRoom("doctor-1")).thenReturn(Optional.of(new MedicalOrderRepository.StaffRoom("doctor-1", "room-1")));
        when(storage.put(any(), any(), org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.eq("image/png"))).thenReturn("bucket");
        when(reports.attachment(any(), any(), any(), any(), org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.eq("bucket"), org.mockito.ArgumentMatchers.eq("doctor-1")))
                .thenReturn(attachment);

        assertThat(service.upload("order-1", file, "doctor-1")).isEqualTo(attachment);

        MockMultipartFile broken = new MockMultipartFile("file", "ct.png", "image/png", new byte[0]) {
            @Override
            public java.io.InputStream getInputStream() throws IOException {
                throw new IOException("broken");
            }
        };
        assertThatThrownBy(() -> service.upload("order-1", broken, "doctor-1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void submitCtAndRefreshCoverSuccessTimeoutAndDraftPersistence() {
        MedicalOrder order = order("order-1", "CHECK", "patient-1", "doctor-1", "room-1", "WAITING");
        MedicalAttachment attachment = attachment("attachment-1", "order-1");
        AiMedicalTask pendingTask = task("task-1", "order-1", "external-1", "PENDING");
        AiMedicalTask completedTask = task("task-1", "order-1", "external-1", "COMPLETED");
        when(orders.findById("order-1")).thenReturn(Optional.of(order));
        when(orders.staffRoom("doctor-1")).thenReturn(Optional.of(new MedicalOrderRepository.StaffRoom("doctor-1", "room-1")));
        when(reports.attachments("order-1")).thenReturn(List.of(attachment));
        when(ai.submit("order-1", attachment.objectKey(), "HEAD", "purpose")).thenReturn(Map.of("taskId", "external-1"));
        when(reports.createTask("order-1", "external-1")).thenReturn(pendingTask);
        when(reports.taskByExternal("external-1")).thenReturn(Optional.of(pendingTask));
        when(reports.timeoutTaskIfExpired("external-1", 360)).thenReturn(Optional.empty());
        when(ai.task("external-1")).thenReturn(Map.of(
                "status", "COMPLETED",
                "modelVersion", "v1",
                "result", Map.of("findings", "f", "conclusion", "c", "riskAdvice", "a")));
        when(reports.updateTask(org.mockito.ArgumentMatchers.eq("external-1"), org.mockito.ArgumentMatchers.eq("COMPLETED"),
                org.mockito.ArgumentMatchers.eq("v1"), any(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(completedTask);
        when(reports.reportByOrder("order-1")).thenReturn(Optional.empty());

        assertThat(service.submitCt("order-1", "attachment-1", "doctor-1")).isEqualTo(pendingTask);
        assertThat(service.refresh("external-1", "doctor-1")).isEqualTo(completedTask);
        verify(reports).saveDraft("order-1", "CHECK", "f", "c", "a", "AI", "task-1");
    }

    @Test
    void confirmRejectListAndAttachmentAccessCoverMainBranches() {
        MedicalOrder waiting = order("order-1", "CHECK", "patient-1", "doctor-1", "room-1", "WAITING");
        MedicalOrder inProgress = order("order-1", "CHECK", "patient-1", "doctor-1", "room-1", "IN_PROGRESS");
        MedicalOrder completed = order("order-1", "CHECK", "patient-1", "doctor-1", "room-1", "COMPLETED");
        MedicalReport draft = report("report-1", "order-1", "DRAFT", "AI");
        MedicalReport confirmed = report("report-1", "order-1", "CONFIRMED", "AI");
        MedicalAttachment attachment = attachment("attachment-1", "order-1");
        when(orders.findById("order-1")).thenReturn(Optional.of(waiting), Optional.of(waiting), Optional.of(waiting), Optional.of(completed), Optional.of(completed), Optional.of(completed));
        when(orders.staffRoom("doctor-1")).thenReturn(Optional.of(new MedicalOrderRepository.StaffRoom("doctor-1", "room-1")));
        when(reports.reportByOrder("order-1")).thenReturn(Optional.of(draft));
        when(orderService.start("order-1", "doctor-1", "CHECK_DOCTOR")).thenReturn(inProgress);
        when(reports.confirm("order-1", "findings", "conclusion", "advice", "doctor-1")).thenReturn(confirmed);
        when(orderService.complete("order-1", "doctor-1", "CHECK_DOCTOR", "conclusion", "AI", "ai-task-1")).thenReturn(completed);
        when(reports.reject("order-1", "doctor-1", "reason")).thenReturn(report("report-2", "order-1", "REJECTED", "HUMAN"));
        when(reports.reports()).thenReturn(List.of(confirmed));
        when(patientAccessClient.boundPatientId("account-1")).thenReturn("patient-1");
        when(patientAccessClient.owns("account-1", "patient-1")).thenReturn(true);
        when(reports.attachments("order-1")).thenReturn(List.of(attachment));
        when(storage.get(attachment.objectKey())).thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));

        assertThat(service.confirm("order-1", "findings", "conclusion", "advice", "doctor-1", "CHECK_DOCTOR")).isEqualTo(confirmed);
        assertThat(service.reject("order-1", "reason", "doctor-1", "CHECK_DOCTOR").status()).isEqualTo("REJECTED");
        assertThat(service.list(null, "account-1", "PATIENT")).containsExactly(confirmed);
        assertThat(service.attachments("order-1", "account-1", "PATIENT")).containsExactly(attachment);
        assertThat(service.attachmentContent("order-1", "attachment-1", "account-1", "PATIENT").attachment()).isEqualTo(attachment);
        verify(workflow).publish(completed, confirmed);
    }

    @Test
    void listAndAccessRejectUnauthorizedUsersAndBlankConclusion() {
        MedicalOrder order = order("order-1", "CHECK", "patient-1", "doctor-1", "room-1", "WAITING");
        when(orders.findById("order-1")).thenReturn(Optional.of(order), Optional.of(order));
        when(orders.staffRoom("doctor-2")).thenReturn(Optional.of(new MedicalOrderRepository.StaffRoom("doctor-2", "room-2")));
        when(reports.reportByOrder("order-1")).thenReturn(Optional.of(
                new MedicalReport("report-1", "order-1", "CHECK", "DRAFT", "findings", "", "advice", "HUMAN", null, null, null, false, null, null, null, LocalDateTime.now())));
        when(orders.staffRoom("doctor-1")).thenReturn(Optional.of(new MedicalOrderRepository.StaffRoom("doctor-1", "room-1")));
        when(patientAccessClient.boundPatientId("account-1")).thenReturn(null);

        assertThatThrownBy(() -> service.confirm("order-1", "", "", "", "doctor-1", "CHECK_DOCTOR"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.attachments("order-1", "doctor-2", "CHECK_DOCTOR"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.list(null, "account-1", "PATIENT"))
                .isInstanceOf(AccessDeniedException.class);
    }

    private MedicalOrder order(String id, String type, String patientId, String doctorId, String roomId, String status) {
        return new MedicalOrder(
                id,
                "appt-1",
                patientId,
                "Patient",
                doctorId,
                type,
                "ITEM",
                "Item",
                "purpose",
                "HEAD",
                BigDecimal.TEN,
                "PAID",
                status,
                roomId,
                "Room 1",
                "Floor 1",
                "staff-1",
                1,
                "ROUTINE",
                null,
                null,
                0,
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.now(),
                null,
                null);
    }

    private MedicalAttachment attachment(String id, String orderId) {
        return new MedicalAttachment(id, orderId, "orders/" + orderId + "/a.png", "a.png", "image/png", 3, "bucket", "doctor-1", LocalDateTime.now());
    }

    private MedicalReport report(String id, String orderId, String status, String source) {
        return new MedicalReport(id, orderId, "CHECK", status, "findings", "conclusion", "advice", source, "ai-task-1", "ai-findings", "ai-conclusion", false, "doctor-1", LocalDateTime.now(), null, LocalDateTime.now());
    }

    private AiMedicalTask task(String id, String orderId, String externalId, String status) {
        return new AiMedicalTask(id, orderId, externalId, "CT_ANALYSIS", status, "v1", "{}", null, LocalDateTime.now(), LocalDateTime.now());
    }
}
