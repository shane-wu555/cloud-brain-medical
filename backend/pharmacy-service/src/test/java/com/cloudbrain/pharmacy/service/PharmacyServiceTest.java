package com.cloudbrain.pharmacy.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.cloudbrain.pharmacy.controller.PharmacyController;
import com.cloudbrain.pharmacy.entity.Prescription;
import com.cloudbrain.pharmacy.entity.PrescriptionItem;
import com.cloudbrain.pharmacy.entity.PrescriptionStatus;
import com.cloudbrain.pharmacy.repository.PharmacyRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PharmacyServiceTest {
    private final PharmacyRepository repository = mock(PharmacyRepository.class);
    private final PharmacyService service = new PharmacyService(repository);

    @Test
    void unpaidPrescriptionCannotBeDispensed() {
        when(repository.findPrescription("rx-1")).thenReturn(prescription(PrescriptionStatus.PENDING_PAYMENT));

        assertThatThrownBy(() -> service.dispense("rx-1", "pharmacist"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已缴费");
        verify(repository, never()).deductStock(any(), any(), anyInt(), any());
    }

    @Test
    void dispensedPrescriptionCannotBeDispensedAgain() {
        when(repository.findPrescription("rx-1")).thenReturn(prescription(PrescriptionStatus.DISPENSED));

        assertThatThrownBy(() -> service.dispense("rx-1", "pharmacist"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("待发药");
    }

    @Test
    void onlyDispensedPrescriptionCanBeReturned() {
        when(repository.findPrescription("rx-1")).thenReturn(prescription(PrescriptionStatus.WAITING_DISPENSE));

        assertThatThrownBy(() -> service.returnDrugs("rx-1", "pharmacist", "患者退药"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已发药");
        verify(repository, never()).restoreStock(any(), any(), anyInt(), any(), any());
    }

    @Test
    void acceptedAiSuggestionMustHaveTraceId() {
        var request = new PharmacyController.CreatePrescriptionRequest("appt-1", "mr-1", "patient-1",
                "张三", "脑供血不足", null, "AI_ACCEPTED", null,
                List.of(new PharmacyController.PrescriptionItemRequest("drug-1", 1, "100mg", "口服", "每日一次", 7, null)));

        assertThatThrownBy(() -> service.prescribe(request, "doctor-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aiAssistanceId");
    }

    private Prescription prescription(PrescriptionStatus status) {
        return new Prescription("rx-1", "RX1", "appt-1", "mr-1", "patient-1", "张三",
                "doctor-1", "诊断", status, BigDecimal.TEN, null, null, "HUMAN_ONLY",
                null, null, null, null, null, null, null, null, null,
                List.of(new PrescriptionItem("item-1", "rx-1", "drug-1", "药品", 2,
                        "100mg", "口服", "每日一次", 7, null, BigDecimal.ONE, BigDecimal.valueOf(2))));
    }
}
