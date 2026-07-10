package com.cloudbrain.medicalorder.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.cloudbrain.medicalorder.domain.LaboratoryResultItem;
import com.cloudbrain.medicalorder.domain.Specimen;
import com.cloudbrain.medicalorder.service.LaboratoryWorkflowService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class LaboratoryWorkflowControllerTest {
    private final LaboratoryWorkflowService service = Mockito.mock(LaboratoryWorkflowService.class);
    private final LaboratoryWorkflowController controller = new LaboratoryWorkflowController(service);

    @Test
    void endpointsDelegateToService() {
        Specimen specimen = new Specimen(
                "specimen-1",
                "order-1",
                "BLOOD",
                "LAB-1",
                "REQUESTED",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.now());
        LaboratoryResultItem item = new LaboratoryResultItem(
                "result-1",
                "order-1",
                "specimen-1",
                "WBC",
                "WBC",
                "10.0",
                "%",
                "3-10",
                "NORMAL",
                "HUMAN",
                null,
                "doctor-1",
                LocalDateTime.now(),
                LocalDateTime.now());
        when(service.create("order-1", "BLOOD", "LAB-1")).thenReturn(specimen);
        when(service.specimens("order-1")).thenReturn(List.of(specimen));
        when(service.transition("specimen-1", "COLLECTED", "doctor-1", "reason")).thenReturn(specimen);
        when(service.saveResults("order-1", "specimen-1", List.of(
                new LaboratoryWorkflowController.ResultItemRequest("WBC", "WBC", "10.0", "%", "3-10", "NORMAL", "HUMAN", null)), "doctor-1"))
                .thenReturn(List.of(item));
        when(service.results("order-1")).thenReturn(List.of(item));

        assertThat(controller.create("order-1", new LaboratoryWorkflowController.CreateSpecimenRequest("BLOOD", "LAB-1"))).isEqualTo(specimen);
        assertThat(controller.specimens("order-1")).containsExactly(specimen);
        assertThat(controller.transition("specimen-1", new LaboratoryWorkflowController.StatusRequest("COLLECTED", "reason"), auth("doctor-1")))
                .isEqualTo(specimen);
        assertThat(controller.saveResults(
                        "order-1",
                        new LaboratoryWorkflowController.SaveResultsRequest(
                                "specimen-1",
                                List.of(new LaboratoryWorkflowController.ResultItemRequest("WBC", "WBC", "10.0", "%", "3-10", "NORMAL", "HUMAN", null))),
                        auth("doctor-1")))
                .containsExactly(item);
        assertThat(controller.results("order-1")).containsExactly(item);
    }

    private JwtAuthenticationToken auth(String subject) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .build();
        return new JwtAuthenticationToken(jwt);
    }
}
