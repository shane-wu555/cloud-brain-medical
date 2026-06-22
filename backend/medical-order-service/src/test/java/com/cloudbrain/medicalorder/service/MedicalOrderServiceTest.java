package com.cloudbrain.medicalorder.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.cloudbrain.medicalorder.domain.MedicalOrder;
import com.cloudbrain.medicalorder.repository.MedicalOrderRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MedicalOrderServiceTest {
    @Test
    void rejectsDoctorRoleThatDoesNotMatchOrderType() {
        MedicalOrderRepository repository = Mockito.mock(MedicalOrderRepository.class);
        MedicalOrder order = new MedicalOrder(
                "order", "appointment", "patient", "患者", "doctor", "LAB", "lab", "血常规",
                null, null, BigDecimal.ZERO, "PAID", "WAITING", null, null, null, LocalDateTime.now(), null, null);
        when(repository.findById("order")).thenReturn(Optional.of(order));
        MedicalOrderService service = new MedicalOrderService(repository);
        assertThrows(IllegalStateException.class, () -> service.start("order", "check-doctor", "CHECK_DOCTOR"));
    }
}
