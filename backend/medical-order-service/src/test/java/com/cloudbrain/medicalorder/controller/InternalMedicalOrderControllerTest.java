package com.cloudbrain.medicalorder.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.cloudbrain.medicalorder.domain.MedicalOrder;
import com.cloudbrain.medicalorder.service.MedicalOrderService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class InternalMedicalOrderControllerTest {
    private final MedicalOrderService service = Mockito.mock(MedicalOrderService.class);
    private final InternalMedicalOrderController controller = new InternalMedicalOrderController(service, "internal-key");

    @Test
    void confirmRequiresValidApiKey() {
        when(service.pay("order-1", "patient-1", "PATIENT")).thenReturn(order());

        assertThatThrownBy(() -> controller.confirm("order-1", "bad-key", new InternalMedicalOrderController.Command("patient-1", "payment-1")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.confirm("order-1", "internal-key", new InternalMedicalOrderController.Command("patient-1", "payment-1")).id())
                .isEqualTo("order-1");
    }

    private MedicalOrder order() {
        return new MedicalOrder(
                "order-1",
                "appt-1",
                "patient-1",
                "Patient",
                "doctor-1",
                "CHECK",
                "ITEM",
                "Item",
                null,
                null,
                BigDecimal.TEN,
                "PAID",
                "WAITING",
                "room-1",
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
}
