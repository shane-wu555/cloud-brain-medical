package com.cloudbrain.medicalorder.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.medicalorder.controller.LaboratoryWorkflowController;
import com.cloudbrain.medicalorder.domain.Specimen;
import com.cloudbrain.medicalorder.repository.LaboratoryWorkflowRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LaboratoryWorkflowServiceTest {
    @Test
    void normalizesArrowAbnormalFlagsBeforeSaving() {
        LaboratoryWorkflowRepository repository = mock(LaboratoryWorkflowRepository.class);
        Specimen specimen = new Specimen(
                "specimen", "order", "全血", "LAB-1", "ANALYZING",
                "collector", LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(),
                null, null, null, null, LocalDateTime.now());
        when(repository.specimen("specimen")).thenReturn(Optional.of(specimen));
        when(repository.results("order")).thenReturn(List.of());

        LaboratoryWorkflowService service = new LaboratoryWorkflowService(repository);
        service.saveResults("order", "specimen", List.of(
                new LaboratoryWorkflowController.ResultItemRequest("WBC", "白细胞计数", "11.2", "×10⁹/L", "3.50-9.50", "↑", "HUMAN", null),
                new LaboratoryWorkflowController.ResultItemRequest("LYMPH", "淋巴细胞百分比", "18.3", "%", "20.0-50.0", "↓", "HUMAN", null)
        ), "doctor");

        verify(repository).upsertResult(eq("order"), eq("specimen"), eq("WBC"), eq("白细胞计数"),
                eq("11.2"), eq("×10⁹/L"), eq("3.50-9.50"), eq("HIGH"), eq("HUMAN"), eq(null), eq("doctor"));
        verify(repository).upsertResult(eq("order"), eq("specimen"), eq("LYMPH"), eq("淋巴细胞百分比"),
                eq("18.3"), eq("%"), eq("20.0-50.0"), eq("LOW"), eq("HUMAN"), eq(null), eq("doctor"));
    }
}
