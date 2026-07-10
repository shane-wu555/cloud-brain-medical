package com.cloudbrain.medicalorder.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.cloudbrain.medicalorder.domain.AiMedicalTask;
import com.cloudbrain.medicalorder.domain.MedicalAttachment;
import com.cloudbrain.medicalorder.domain.MedicalReport;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class MedicalReportRepositoryTest {
    private final JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);

    @Test
    void attachmentTaskAndReportLifecycleMethodsUseJdbc() {
        MedicalReportRepository repository = Mockito.spy(new MedicalReportRepository(jdbc));
        AiMedicalTask task = task("task-1", "order-1", "external-1", "PENDING");
        MedicalReport report = report("report-1", "order-1", "DRAFT", "AI");
        doReturn(Optional.of(task)).when(repository).taskByExternal("external-1");
        doReturn(Optional.of(report)).when(repository).reportByOrder("order-1");
        when(jdbc.update(contains("status = 'FAILED'"), any(), eq("external-1"), eq(360L))).thenReturn(1);
        when(jdbc.update(contains("set status = ?"), eq("COMPLETED"), eq("v1"), eq("{}"), eq(null), eq("external-1"))).thenReturn(1);
        when(jdbc.update(contains("status = 'CONFIRMED'"), eq("findings"), eq("conclusion"), eq("advice"), eq("findings"), eq("conclusion"), eq("doctor-1"), eq("order-1"))).thenReturn(1);
        when(jdbc.update(contains("status = 'REJECTED'"), eq("doctor-1"), eq("reason"), eq("order-1"))).thenReturn(1);

        assertThat(repository.createTask("order-1", "external-1")).isEqualTo(task);
        assertThat(repository.timeoutTaskIfExpired("external-1", 360)).contains(task);
        assertThat(repository.updateTask("external-1", "COMPLETED", "v1", null, null)).isEqualTo(task);
        assertThat(repository.saveDraft("order-1", "CHECK", "findings", "conclusion", "advice", "AI", "task-1")).isEqualTo(report);
        assertThat(repository.confirm("order-1", "findings", "conclusion", "advice", "doctor-1")).isEqualTo(report);
        assertThat(repository.reject("order-1", "doctor-1", "reason")).isEqualTo(report);
    }

    @Test
    void selectQueriesAndStatusNormalizationAreCovered() {
        MedicalReportRepository repository = Mockito.spy(new MedicalReportRepository(jdbc));
        MedicalAttachment attachment = attachment("attachment-1", "order-1");
        AiMedicalTask task = task("task-1", "order-1", "external-1", "RUNNING");
        MedicalReport report = report("report-1", "order-1", "CONFIRMED", "HUMAN");
        when(jdbc.query(contains("from attachment"), any(RowMapper.class), eq("order-1"))).thenReturn(List.of(attachment));
        when(jdbc.query(contains("from ai_medical_task where external_task_id"), any(RowMapper.class), eq("external-1"))).thenReturn(List.of(task));
        when(jdbc.query(contains("from medical_report where order_id"), any(RowMapper.class), eq("order-1"))).thenReturn(List.of(report));
        when(jdbc.query(contains("from medical_report order by updated_at desc"), any(RowMapper.class))).thenReturn(List.of(report));
        when(jdbc.update(contains("status = ?, model_version = ?"), eq("PENDING"), eq("v1"), eq("{}"), eq("error"), eq("external-1"))).thenReturn(1);
        doReturn(Optional.of(task)).when(repository).taskByExternal("external-1");

        assertThat(repository.attachments("order-1")).containsExactly(attachment);
        assertThat(repository.taskByExternal("external-1")).contains(task);
        assertThat(repository.reportByOrder("order-1")).contains(report);
        assertThat(repository.reports()).containsExactly(report);
        assertThat(repository.updateTask("external-1", "UNKNOWN", "v1", "{}", "error")).isEqualTo(task);
    }

    private MedicalAttachment attachment(String id, String orderId) {
        return new MedicalAttachment(id, orderId, "orders/" + orderId + "/a.png", "a.png", "image/png", 3, "bucket", "doctor-1", LocalDateTime.now());
    }

    private AiMedicalTask task(String id, String orderId, String externalId, String status) {
        return new AiMedicalTask(id, orderId, externalId, "CT_ANALYSIS", status, "v1", "{}", null, LocalDateTime.now(), LocalDateTime.now());
    }

    private MedicalReport report(String id, String orderId, String status, String source) {
        return new MedicalReport(id, orderId, "CHECK", status, "findings", "conclusion", "advice", source, "task-1", "ai-findings", "ai-conclusion", false, "doctor-1", LocalDateTime.now(), null, LocalDateTime.now());
    }
}
