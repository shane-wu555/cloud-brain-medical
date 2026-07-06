package com.cloudbrain.pharmacy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.pharmacy.audit.AuditPublisher;
import com.cloudbrain.pharmacy.controller.PharmacyController;
import com.cloudbrain.pharmacy.entity.DrugReturnOrder;
import com.cloudbrain.pharmacy.entity.DrugReturnStatus;
import com.cloudbrain.pharmacy.entity.Prescription;
import com.cloudbrain.pharmacy.entity.PrescriptionItem;
import com.cloudbrain.pharmacy.entity.PrescriptionStatus;
import com.cloudbrain.pharmacy.repository.PharmacyRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PharmacyServiceTest {
    private final PharmacyRepository repository = mock(PharmacyRepository.class);
    private final PatientAccessClient patientAccessClient = mock(PatientAccessClient.class);
    private final AuditPublisher auditPublisher = mock(AuditPublisher.class);
    private final PharmacyService service = new PharmacyService(repository, patientAccessClient, auditPublisher);

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
        when(repository.markReturnedBeforeDispense(
                "30000000-0000-4000-8000-000000000001",
                "pharmacist",
                "return",
                PrescriptionStatus.RETURN_PENDING_REFUND)).thenReturn(true);

        service.returnDrugs("30000000-0000-4000-8000-000000000001", "pharmacist", "return");

        verify(repository, never()).restoreStock(any(), any(), anyInt(), any(), any());
        verify(repository).markReturnedBeforeDispense(
                "30000000-0000-4000-8000-000000000001",
                "pharmacist",
                "return",
                PrescriptionStatus.RETURN_PENDING_REFUND);
    }

    @Test
    void unpaidPrescriptionReturnsToCompletedReturnWithoutRefund() {
        when(repository.findPrescription("30000000-0000-4000-8000-000000000001"))
                .thenReturn(prescription(PrescriptionStatus.PENDING_PAYMENT))
                .thenReturn(prescription(PrescriptionStatus.RETURNED));
        when(repository.markReturnedBeforeDispense(
                "30000000-0000-4000-8000-000000000001",
                "doctor",
                "return",
                PrescriptionStatus.RETURNED)).thenReturn(true);

        service.returnDrugs("30000000-0000-4000-8000-000000000001", "doctor", "return");

        verify(repository).markReturnedBeforeDispense(
                "30000000-0000-4000-8000-000000000001",
                "doctor",
                "return",
                PrescriptionStatus.RETURNED);
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
        var drug = new PharmacyRepository.Drug(
                "drug-1", "DRUG-1", "test drug", "10mg", "box",
                BigDecimal.TEN, "tablet", "room", 35, 10);
        when(repository.drug("drug-1")).thenReturn(drug);

        PharmacyRepository.Drug updated = service.addStock(
                "drug-1",
                new PharmacyController.StockInRequest(5, "purchase"),
                "pharmacist");

        assertThat(updated.quantity()).isEqualTo(35);
        verify(repository).addStock("drug-1", 5, "pharmacist", "purchase");
    }

    @Test
    void stockInQuantityMustBePositive() {
        assertThatThrownBy(() -> service.addStock(
                "drug-1",
                new PharmacyController.StockInRequest(0, "purchase"),
                "pharmacist"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).addStock(any(), anyInt(), any(), any());
    }

    @Test
    void prescriptionListAuditedAsSingleListAccess() {
        when(repository.list(null, null)).thenReturn(List.of(
                prescription(PrescriptionStatus.DISPENSED),
                prescription(PrescriptionStatus.RETURN_PENDING_REFUND)));

        List<Prescription> prescriptions = service.list(null, null, null, "cashier-1", "CASHIER");

        assertThat(prescriptions).hasSize(2);
        verify(auditPublisher).publish(
                eq("PRESCRIPTION_LIST_VIEW"),
                eq("PRESCRIPTION"),
                isNull(),
                isNull(),
                isNull(),
                eq("cashier-1"),
                eq("CASHIER"),
                eq(Map.of("accessScope", "LIST", "statusFilter", "ALL", "resultCount", 2)));
    }

    @Test
    void prescriptionListViewFiltersAndAuditsVisibleCount() {
        List<PrescriptionStatus> statuses = List.of(PrescriptionStatus.CONFIRMED, PrescriptionStatus.PENDING_PAYMENT);
        when(repository.listByStatuses(null, statuses)).thenReturn(List.of(prescription(PrescriptionStatus.CONFIRMED)));

        List<Prescription> prescriptions = service.list(null, null, "OUTPATIENT_PAYMENT", "cashier-1", "CASHIER");

        assertThat(prescriptions).hasSize(1);
        verify(auditPublisher).publish(
                eq("PRESCRIPTION_LIST_VIEW"),
                eq("PRESCRIPTION"),
                isNull(),
                isNull(),
                isNull(),
                eq("cashier-1"),
                eq("CASHIER"),
                eq(Map.of(
                        "accessScope", "LIST",
                        "view", "OUTPATIENT_PAYMENT",
                        "auditSummary", "查看了门诊缴费项目（含处方信息）",
                        "statusFilter", List.of("CONFIRMED", "PENDING_PAYMENT"),
                        "resultCount", 1)));
    }

    @Test
    void paymentRecordPrescriptionContextAuditsRemarkWithoutStatusCount() {
        when(repository.list(null, null)).thenReturn(List.of(
                prescription(PrescriptionStatus.DISPENSED),
                prescription(PrescriptionStatus.RETURN_PENDING_REFUND)));

        List<Prescription> prescriptions = service.list(null, null, "PAYMENT_RECORD", "cashier-1", "CASHIER");

        assertThat(prescriptions).hasSize(2);
        verify(auditPublisher).publish(
                eq("PRESCRIPTION_LIST_VIEW"),
                eq("PRESCRIPTION"),
                isNull(),
                isNull(),
                isNull(),
                eq("cashier-1"),
                eq("CASHIER"),
                eq(Map.of(
                        "accessScope", "LIST",
                        "view", "PAYMENT_RECORD",
                        "auditSummary", "查看了缴费退费记录（含处方信息）",
                        "relatedPrescriptionCount", 2)));
    }

    @Test
    void drugReturnListAuditedEvenWhenEmpty() {
        when(repository.listDrugReturns(null, "RETURN_PENDING_REFUND")).thenReturn(List.<DrugReturnOrder>of());

        List<DrugReturnOrder> orders = service.drugReturns(null, "RETURN_PENDING_REFUND", "cashier-1", "CASHIER");

        assertThat(orders).isEmpty();
        verify(auditPublisher).publish(
                eq("DRUG_RETURN_LIST_VIEW"),
                eq("DRUG_RETURN"),
                isNull(),
                isNull(),
                isNull(),
                eq("cashier-1"),
                eq("CASHIER"),
                eq(Map.of("accessScope", "LIST", "statusFilter", "RETURN_PENDING_REFUND", "resultCount", 0)));
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
