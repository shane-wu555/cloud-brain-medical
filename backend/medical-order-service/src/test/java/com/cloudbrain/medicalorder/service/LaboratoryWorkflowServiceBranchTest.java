package com.cloudbrain.medicalorder.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.medicalorder.controller.LaboratoryWorkflowController;
import com.cloudbrain.medicalorder.domain.LaboratoryResultItem;
import com.cloudbrain.medicalorder.domain.Specimen;
import com.cloudbrain.medicalorder.repository.LaboratoryWorkflowRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LaboratoryWorkflowServiceBranchTest {
    private final LaboratoryWorkflowRepository repository = Mockito.mock(LaboratoryWorkflowRepository.class);
    private final LaboratoryWorkflowService service = new LaboratoryWorkflowService(repository);

    @Test
    void createValidatesLabOrderAndRequiredFields() {
        when(repository.isLabOrder("order-1")).thenReturn(false, true, true, true);
        when(repository.createSpecimen("order-1", "BLOOD", "LAB-1")).thenReturn(specimen("order-1", "REQUESTED"));

        assertThatThrownBy(() -> service.create("order-1", "BLOOD", "LAB-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create("order-1", " ", "LAB-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create("order-1", "BLOOD", " "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(service.create("order-1", "BLOOD", "LAB-1").barcode()).isEqualTo("LAB-1");
    }

    @Test
    void transitionValidatesAllowedStateDiscardReasonAndOptimisticLock() {
        when(repository.specimen("specimen-1")).thenReturn(
                Optional.of(specimen("order-1", "REQUESTED")),
                Optional.of(specimen("order-1", "REQUESTED")),
                Optional.of(specimen("order-1", "REQUESTED")),
                Optional.of(specimen("order-1", "REQUESTED")),
                Optional.of(specimen("order-1", "COLLECTED")));
        when(repository.transition("specimen-1", "REQUESTED", "COLLECTED", "checker-1", null)).thenReturn(false, true);

        assertThatThrownBy(() -> service.transition("specimen-1", "REVIEWED", "checker-1", null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.transition("specimen-1", "DISCARDED", "checker-1", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.transition("specimen-1", "COLLECTED", "checker-1", null))
                .isInstanceOf(IllegalStateException.class);

        assertThat(service.transition("specimen-1", "COLLECTED", "checker-1", null).status())
                .isEqualTo("COLLECTED");
    }

    @Test
    void saveResultsValidatesSpecimenOwnershipStatusAndItems() {
        when(repository.specimen("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.saveResults("order-1", "missing", List.of(item("WBC", "1.0", null, "HUMAN", null)), "doctor"))
                .isInstanceOf(IllegalArgumentException.class);

        when(repository.specimen("specimen-1")).thenReturn(Optional.of(specimen("order-2", "ANALYZING")));
        assertThatThrownBy(() -> service.saveResults("order-1", "specimen-1", List.of(item("WBC", "1.0", null, "HUMAN", null)), "doctor"))
                .isInstanceOf(IllegalArgumentException.class);

        when(repository.specimen("specimen-1")).thenReturn(Optional.of(specimen("order-1", "RECEIVED")));
        assertThatThrownBy(() -> service.saveResults("order-1", "specimen-1", List.of(item("WBC", "1.0", null, "HUMAN", null)), "doctor"))
                .isInstanceOf(IllegalStateException.class);

        when(repository.specimen("specimen-1")).thenReturn(Optional.of(specimen("order-1", "ANALYZING")));
        assertThatThrownBy(() -> service.saveResults("order-1", "specimen-1", List.of(), "doctor"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void saveResultsValidatesSourceAndNormalizesFlags() {
        when(repository.specimen("specimen-1")).thenReturn(Optional.of(specimen("order-1", "ANALYZING")));
        when(repository.results("order-1")).thenReturn(List.of(
                new LaboratoryResultItem("r1", "order-1", "specimen-1", "WBC", "WBC", "1.0", "%", "1-2", "NORMAL", "HUMAN", null, "doctor", null, LocalDateTime.now())));

        assertThatThrownBy(() -> service.saveResults(
                        "order-1",
                        "specimen-1",
                        List.of(item("WBC", "1.0", null, "robot", null)),
                        "doctor"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.saveResults(
                        "order-1",
                        "specimen-1",
                        List.of(item("WBC", "1.0", "C", "AI", " ")),
                        "doctor"))
                .isInstanceOf(IllegalArgumentException.class);

        List<LaboratoryResultItem> results = service.saveResults(
                "order-1",
                "specimen-1",
                List.of(
                        item("WBC", "1.0", "", null, null),
                        item("CRP", "12.0", "C", "AI", "ai-1")),
                "doctor");

        assertThat(results).hasSize(1);
        verify(repository).upsertResult(
                eq("order-1"),
                eq("specimen-1"),
                eq("WBC"),
                eq("WBC"),
                eq("1.0"),
                eq("%"),
                eq("1-2"),
                eq("NORMAL"),
                eq("HUMAN"),
                eq(null),
                eq("doctor"));
        verify(repository).upsertResult(
                eq("order-1"),
                eq("specimen-1"),
                eq("CRP"),
                eq("CRP"),
                eq("12.0"),
                eq("%"),
                eq("1-2"),
                eq("CRITICAL"),
                eq("AI"),
                eq("ai-1"),
                eq("doctor"));
    }

    private Specimen specimen(String orderId, String status) {
        return new Specimen(
                "specimen-1",
                orderId,
                "BLOOD",
                "LAB-1",
                status,
                "collector",
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                null,
                null,
                null,
                LocalDateTime.now());
    }

    private LaboratoryWorkflowController.ResultItemRequest item(
            String code, String value, String flag, String createdByType, String aiRecordId) {
        return new LaboratoryWorkflowController.ResultItemRequest(
                code, code, value, "%", "1-2", flag, createdByType, aiRecordId);
    }
}
