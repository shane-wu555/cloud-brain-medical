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
    private final PatientAccessClient patientAccessClient = mock(PatientAccessClient.class);
    private final PharmacyService service = new PharmacyService(repository, patientAccessClient);

    @Test
    void unpaidPrescriptionCannotBeDispensed() {
        when(repository.findPrescription("30000000-0000-4000-8000-000000000001")).thenReturn(prescription(PrescriptionStatus.PENDING_PAYMENT));

        assertThatThrownBy(() -> service.dispense("30000000-0000-4000-8000-000000000001", "pharmacist"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已缴费");
        verify(repository, never()).deductStock(any(), any(), anyInt(), any());
    }

    @Test
    void dispensedPrescriptionCannotBeDispensedAgain() {
        when(repository.findPrescription("30000000-0000-4000-8000-000000000001")).thenReturn(prescription(PrescriptionStatus.DISPENSED));

        assertThatThrownBy(() -> service.dispense("30000000-0000-4000-8000-000000000001", "pharmacist"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("待发药");
    }

    @Test
    void onlyDispensedPrescriptionCanBeReturned() {
        when(repository.findPrescription("30000000-0000-4000-8000-000000000001")).thenReturn(prescription(PrescriptionStatus.WAITING_DISPENSE));

        assertThatThrownBy(() -> service.returnDrugs("30000000-0000-4000-8000-000000000001", "pharmacist", "患者退药"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已发药");
        verify(repository, never()).restoreStock(any(), any(), anyInt(), any(), any());
    }

    @Test
    void acceptedAiSuggestionMustHaveTraceId() {
        var request = new PharmacyController.CreatePrescriptionRequest("00000000-0000-4000-8000-000000000001", "mr-1", "a0000000-0000-4000-8000-000000000001",
                "张三", "脑供血不足", null, "AI_ACCEPTED", null,
                List.of(new PharmacyController.PrescriptionItemRequest("40000000-0000-4000-8000-000000000001", 1, "100mg", "口服", "每日一次", 7, null)));

        assertThatThrownBy(() -> service.prescribe(request, "doctor-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aiAssistanceId");
    }

    private Prescription prescription(PrescriptionStatus status) {
        return new Prescription("30000000-0000-4000-8000-000000000001", "RX1", "00000000-0000-4000-8000-000000000001", "mr-1", "a0000000-0000-4000-8000-000000000001", "张三",
                "doctor-1", "诊断", status, BigDecimal.TEN, null, null, "HUMAN_ONLY",
                null, null, null, null, null, null, null, null, null,
                List.of(new PrescriptionItem("50000000-0000-4000-8000-000000000001", "30000000-0000-4000-8000-000000000001", "40000000-0000-4000-8000-000000000001", "药品", 2,
                        "100mg", "口服", "每日一次", 7, null, BigDecimal.ONE, BigDecimal.valueOf(2))));
    }
}
