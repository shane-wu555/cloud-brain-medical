package com.cloudbrain.medicalorder.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.cloudbrain.medicalorder.domain.AiMedicalTask;
import com.cloudbrain.medicalorder.domain.MedicalAttachment;
import com.cloudbrain.medicalorder.domain.MedicalReport;
import com.cloudbrain.medicalorder.service.MedicalReportService;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class MedicalReportControllerTest {
    private final MedicalReportService service = Mockito.mock(MedicalReportService.class);
    private final MedicalReportController controller = new MedicalReportController(service);

    @Test
    void endpointsDelegateToServiceAndWrapAttachmentDownload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "ct.png", "image/png", new byte[] {1, 2, 3});
        MedicalAttachment attachment = attachment("attachment-1", "order-1");
        MedicalReport report = report("report-1", "order-1");
        AiMedicalTask task = task("task-1", "order-1");
        when(service.upload("order-1", file, "doctor-1")).thenReturn(attachment);
        when(service.attachments("order-1", "doctor-1", "CHECK_DOCTOR")).thenReturn(List.of(attachment));
        when(service.attachmentContent("order-1", "attachment-1", "doctor-1", "CHECK_DOCTOR"))
                .thenReturn(new MedicalReportService.AttachmentDownload(attachment, new ByteArrayInputStream(new byte[] {1, 2, 3})));
        when(service.submitCt("order-1", "attachment-1", "doctor-1")).thenReturn(task);
        when(service.refresh("task-1", "doctor-1")).thenReturn(task);
        when(service.manualDraft("order-1", "findings", "conclusion", "advice", "doctor-1")).thenReturn(report);
        when(service.confirm("order-1", "findings", "conclusion", "advice", "doctor-1", "CHECK_DOCTOR")).thenReturn(report);
        when(service.reject("order-1", "reason", "doctor-1", "CHECK_DOCTOR")).thenReturn(report);
        when(service.list("patient-1", "doctor-1", "CHECK_DOCTOR")).thenReturn(List.of(report));

        assertThat(controller.upload("order-1", file, auth("doctor-1", "CHECK_DOCTOR"))).isEqualTo(attachment);
        assertThat(controller.attachments("order-1", auth("doctor-1", "CHECK_DOCTOR"))).containsExactly(attachment);
        assertThat(controller.attachmentContent("order-1", "attachment-1", auth("doctor-1", "CHECK_DOCTOR"))
                        .getHeaders()
                        .getContentType()
                        .toString())
                .isEqualTo("image/png");
        assertThat(controller.analyze("order-1", new MedicalReportController.CtRequest("attachment-1"), auth("doctor-1", "CHECK_DOCTOR")))
                .isEqualTo(task);
        assertThat(controller.task("task-1", auth("doctor-1", "CHECK_DOCTOR"))).isEqualTo(task);
        assertThat(controller.draft("order-1", new MedicalReportController.ReportRequest("findings", "conclusion", "advice"), auth("doctor-1", "CHECK_DOCTOR")))
                .isEqualTo(report);
        assertThat(controller.confirm("order-1", new MedicalReportController.ReportRequest("findings", "conclusion", "advice"), auth("doctor-1", "CHECK_DOCTOR")))
                .isEqualTo(report);
        assertThat(controller.reject("order-1", new MedicalReportController.RejectRequest("reason"), auth("doctor-1", "CHECK_DOCTOR")))
                .isEqualTo(report);
        assertThat(controller.reports("patient-1", auth("doctor-1", "CHECK_DOCTOR"))).containsExactly(report);
    }

    private JwtAuthenticationToken auth(String subject, String role) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("role", role)
                .build();
        return new JwtAuthenticationToken(jwt);
    }

    private MedicalAttachment attachment(String id, String orderId) {
        return new MedicalAttachment(id, orderId, "orders/" + orderId + "/a.png", "a.png", "image/png", 3, "bucket", "doctor-1", LocalDateTime.now());
    }

    private MedicalReport report(String id, String orderId) {
        return new MedicalReport(id, orderId, "CHECK", "DRAFT", "findings", "conclusion", "advice", "HUMAN", null, null, null, false, "doctor-1", LocalDateTime.now(), null, LocalDateTime.now());
    }

    private AiMedicalTask task(String id, String orderId) {
        return new AiMedicalTask(id, orderId, "external-1", "CT_ANALYSIS", "PENDING", null, "{}", null, LocalDateTime.now(), LocalDateTime.now());
    }
}
