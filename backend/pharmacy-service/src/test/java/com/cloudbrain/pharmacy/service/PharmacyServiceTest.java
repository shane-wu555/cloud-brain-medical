package com.cloudbrain.pharmacy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        when(repository.findPrescription("30000000-0000-4000-8000-000000000001"))
                .thenReturn(prescription(PrescriptionStatus.PENDING_PAYMENT));

        assertThatThrownBy(() -> service.dispense("30000000-0000-4000-8000-000000000001", "pharmacist"))
                .isInstanceOf(IllegalStateException.class);
        verify(repository, never()).deductStock(any(), any(), anyInt(), any());
    }

    @Test
    void dispensedPrescriptionCannotBeDispensedAgain() {
        when(repository.findPrescription("30000000-0000-4000-8000-000000000001"))
                .thenReturn(prescription(PrescriptionStatus.DISPENSED));

        assertThatThrownBy(() -> service.dispense("30000000-0000-4000-8000-000000000001", "pharmacist"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void onlyBeforeDispensePrescriptionCanBeReturned() {
        when(repository.findPrescription("30000000-0000-4000-8000-000000000001"))
                .thenReturn(prescription(PrescriptionStatus.DISPENSED));

        assertThatThrownBy(() -> service.returnDrugs("30000000-0000-4000-8000-000000000001", "pharmacist", "return"))
                .isInstanceOf(IllegalStateException.class);
        verify(repository, never()).restoreStock(any(), any(), anyInt(), any(), any());
        verify(repository, never()).markReturnedBeforeDispense(any(), any(), any(), any());
    }

    @Test
    void waitingDispensePrescriptionReturnsToPendingRefundWithoutStockRestore() {
        when(repository.findPrescription("30000000-0000-4000-8000-000000000001"))
                .thenReturn(prescription(PrescriptionStatus.WAITING_DISPENSE))
                .thenReturn(prescription(PrescriptionStatus.RETURN_PENDING_REFUND));
        when(repository.markReturnedBeforeDispense("30000000-0000-4000-8000-000000000001", "pharmacist",
                "return", PrescriptionStatus.RETURN_PENDING_REFUND)).thenReturn(true);

        service.returnDrugs("30000000-0000-4000-8000-000000000001", "pharmacist", "return");

        verify(repository, never()).restoreStock(any(), any(), anyInt(), any(), any());
        verify(repository).markReturnedBeforeDispense("30000000-0000-4000-8000-000000000001", "pharmacist",
                "return", PrescriptionStatus.RETURN_PENDING_REFUND);
    }

    @Test
    void unpaidPrescriptionReturnsToCompletedReturnWithoutRefund() {
        when(repository.findPrescription("30000000-0000-4000-8000-000000000001"))
                .thenReturn(prescription(PrescriptionStatus.PENDING_PAYMENT))
                .thenReturn(prescription(PrescriptionStatus.RETURNED));
        when(repository.markReturnedBeforeDispense("30000000-0000-4000-8000-000000000001", "doctor",
                "return", PrescriptionStatus.RETURNED)).thenReturn(true);

        service.returnDrugs("30000000-0000-4000-8000-000000000001", "doctor", "return");

        verify(repository).markReturnedBeforeDispense("30000000-0000-4000-8000-000000000001", "doctor",
                "return", PrescriptionStatus.RETURNED);
    }

    @Test
    void acceptedAiSuggestionMustHaveTraceId() {
        var request = new PharmacyController.CreatePrescriptionRequest(
                "00000000-0000-4000-8000-000000000001",
                "mr-1",
                "a0000000-0000-4000-8000-000000000001",
                "patient",
                "diagnosis",
                null,
                "AI_ACCEPTED",
                null,
                List.of(new PharmacyController.PrescriptionItemRequest(
                        "40000000-0000-4000-8000-000000000001",
                        1,
                        "100mg",
                        "oral",
                        "daily",
                        7,
                        null)));

        assertThatThrownBy(() -> service.prescribe(request, "doctor-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aiAssistanceId");
    }

    @Test
    void stockInAddsInventoryAndReturnsUpdatedDrug() {
        var drug = new PharmacyRepository.Drug("drug-1", "DRUG-1", "test drug", "10mg×10片",
                "盒", BigDecimal.TEN, "片剂", "常温", 35, 10);
        when(repository.drug("drug-1")).thenReturn(drug);

        PharmacyRepository.Drug updated = service.addStock("drug-1",
                new PharmacyController.StockInRequest(5, "purchase"), "pharmacist");

        assertThat(updated.quantity()).isEqualTo(35);
        verify(repository).addStock("drug-1", 5, "pharmacist", "purchase");
    }

    @Test
    void stockInQuantityMustBePositive() {
        assertThatThrownBy(() -> service.addStock("drug-1",
                new PharmacyController.StockInRequest(0, "purchase"), "pharmacist"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).addStock(any(), anyInt(), any(), any());
    }

    private Prescription prescription(PrescriptionStatus status) {
        return new Prescription(
                "30000000-0000-4000-8000-000000000001",
                "RX1",
                "00000000-0000-4000-8000-000000000001",
                "mr-1",
                "a0000000-0000-4000-8000-000000000001",
                "patient",
                "doctor-1",
                "diagnosis",
                status,
                BigDecimal.TEN,
                null,
                null,
                "HUMAN_ONLY",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(new PrescriptionItem(
                        "50000000-0000-4000-8000-000000000001",
                        "30000000-0000-4000-8000-000000000001",
                        "40000000-0000-4000-8000-000000000001",
                        "drug",
                        2,
                        "100mg",
                        "oral",
                        "daily",
                        7,
                        null,
                        BigDecimal.ONE,
                        BigDecimal.valueOf(2))));
    }
}
