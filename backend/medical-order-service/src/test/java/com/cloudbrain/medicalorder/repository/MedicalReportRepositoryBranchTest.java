package com.cloudbrain.medicalorder.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.cloudbrain.medicalorder.domain.AiMedicalTask;
import com.cloudbrain.medicalorder.domain.MedicalReport;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

class MedicalReportRepositoryBranchTest {
    private final JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);

    @Test
    void timeoutTaskAndConfirmRejectPathsAreCovered() {
        MedicalReportRepository repository = Mockito.spy(new MedicalReportRepository(jdbc));
        when(jdbc.update(contains("status = 'FAILED'"), eq("AI analysis timed out. Please check the AI service logs and retry."), eq("external-1"), eq(60L)))
                .thenReturn(0);
        assertThat(repository.timeoutTaskIfExpired("external-1", 60)).isEmpty();

        MedicalReport confirmed = report("CONFIRMED");
        when(jdbc.update(contains("status = 'CONFIRMED'"), eq("findings"), eq("conclusion"), eq("advice"), eq("findings"), eq("conclusion"), eq("doctor"), eq("order-1")))
                .thenReturn(0);
        doReturn(Optional.of(confirmed)).when(repository).reportByOrder("order-1");
        assertThat(repository.confirm("order-1", "findings", "conclusion", "advice", "doctor"))
                .isEqualTo(confirmed);

        MedicalReport draft = report("DRAFT");
        doReturn(Optional.of(draft)).when(repository).reportByOrder("order-2");
        assertThatThrownBy(() -> repository.confirm("order-2", "findings", "conclusion", "advice", "doctor"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectAndStatusNormalizationBranchesAreCovered() {
        MedicalReportRepository repository = Mockito.spy(new MedicalReportRepository(jdbc));
        when(jdbc.update(contains("status = 'REJECTED'"), eq("doctor"), eq("reason"), eq("order-1"))).thenReturn(0);
        assertThatThrownBy(() -> repository.reject("order-1", "doctor", "reason"))
                .isInstanceOf(IllegalStateException.class);

        AiMedicalTask task = new AiMedicalTask("task-1", "order-1", "external-1", "CT_ANALYSIS", "PENDING", "v1", "{}", null, LocalDateTime.now(), LocalDateTime.now());
        doReturn(Optional.of(task)).when(repository).taskByExternal("external-1");
        when(jdbc.update(contains("set status = ?, model_version = ?"), eq("RUNNING"), eq("v1"), eq("{}"), eq(null), eq("external-1"))).thenReturn(1);
        when(jdbc.update(contains("set status = ?, model_version = ?"), eq("FAILED"), eq("v1"), eq("{}"), eq("error"), eq("external-1"))).thenReturn(1);
        when(jdbc.update(contains("set status = ?, model_version = ?"), eq("PENDING"), eq("v1"), eq("{}"), eq("error"), eq("external-1"))).thenReturn(1);

        assertThat(repository.updateTask("external-1", "RUNNING", "v1", null, null)).isEqualTo(task);
        assertThat(repository.updateTask("external-1", "FAILED", "v1", null, "error")).isEqualTo(task);
        assertThat(repository.updateTask("external-1", null, "v1", null, "error")).isEqualTo(task);
    }

    private MedicalReport report(String status) {
        return new MedicalReport(
                "report-1",
                "order-1",
                "CHECK",
                status,
                "findings",
                "conclusion",
                "advice",
                "AI",
                "task-1",
                "ai-findings",
                "ai-conclusion",
                false,
                "doctor",
                LocalDateTime.now(),
                null,
                LocalDateTime.now());
    }
}
