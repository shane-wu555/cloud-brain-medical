package com.cloudbrain.pharmacy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class PharmacyServiceTest {
    private final PharmacyRepository repository = mock(PharmacyRepository.class);
    private final PatientAccessClient patientAccessClient = mock(PatientAccessClient.class);
    private final AuditPublisher auditPublisher = mock(AuditPublisher.class);
    private final DrugSearchIndexService drugSearchIndexService = mock(DrugSearchIndexService.class);
    private final PharmacyService service = new PharmacyService(repository, patientAccessClient, auditPublisher, drugSearchIndexService);

    @BeforeEach
    void setUp() {
        when(drugSearchIndexService.index(any())).thenReturn(true);
    }

    @Test
    void drugsUseSearchIndexMatchesBeforeFallingBackToRepositoryQuery() {
        PharmacyRepository.Drug aspirin = drug("drug-1", 20, 5);
        when(drugSearchIndexService.searchDrugIds("aspirin", "ROOM", 100)).thenReturn(Optional.of(List.of("drug-1")));
        when(repository.drugsByIds(List.of("drug-1"))).thenReturn(List.of(aspirin));

        List<PharmacyRepository.Drug> result = service.drugs("aspirin", "ROOM");

        assertThat(result).containsExactly(aspirin);
        verify(repository).drugsByIds(List.of("drug-1"));
        verify(repository, never()).drugs("aspirin", "ROOM");
    }

    @Test
    void drugsFallBackToRepositoryWhenSearchIndexHasNoMatches() {
        PharmacyRepository.Drug aspirin = drug("drug-1", 20, 5);
        when(drugSearchIndexService.searchDrugIds("aspirin", "ROOM", 100)).thenReturn(Optional.of(List.of()));
        when(repository.drugs("aspirin", "ROOM")).thenReturn(List.of(aspirin));

        List<PharmacyRepository.Drug> result = service.drugs("aspirin", "ROOM");

        assertThat(result).containsExactly(aspirin);
        verify(repository).drugs("aspirin", "ROOM");
    }

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
    void dispenseDeductsAllItemsMarksPrescriptionAndAudits() {
        Prescription waiting = prescription(PrescriptionStatus.WAITING_DISPENSE);
        Prescription dispensed = prescription(PrescriptionStatus.DISPENSED);
        when(repository.findPrescription("30000000-0000-4000-8000-000000000001"))
                .thenReturn(waiting)
                .thenReturn(dispensed);
        when(repository.markDispensed("30000000-0000-4000-8000-000000000001", "pharmacist")).thenReturn(true);

        Prescription result = service.dispense("30000000-0000-4000-8000-000000000001", "pharmacist");

        assertThat(result.status()).isEqualTo(PrescriptionStatus.DISPENSED);
        verify(repository).deductStock("40000000-0000-4000-8000-000000000001", "30000000-0000-4000-8000-000000000001", 2, "pharmacist");
        verify(auditPublisher).publish(
                eq("PRESCRIPTION_DISPENSE"),
                eq("PRESCRIPTION"),
                eq("30000000-0000-4000-8000-000000000001"),
                eq("a0000000-0000-4000-8000-000000000001"),
                eq("30000000-0000-4000-8000-000000000001"),
                eq("pharmacist"),
                eq("PHARMACY_STAFF"),
                eq(Map.of("status", "DISPENSED")));
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
    void returnDrugsDefaultsReasonAndAuditsSuccess() {
        when(repository.findPrescription("30000000-0000-4000-8000-000000000001"))
                .thenReturn(prescription(PrescriptionStatus.CONFIRMED))
                .thenReturn(prescription(PrescriptionStatus.RETURNED));
        when(repository.markReturnedBeforeDispense(
                "30000000-0000-4000-8000-000000000001",
                "pharmacist",
                "return-before-dispense",
                PrescriptionStatus.RETURNED)).thenReturn(true);

        Prescription result = service.returnDrugs("30000000-0000-4000-8000-000000000001", "pharmacist", " ");

        assertThat(result.status()).isEqualTo(PrescriptionStatus.RETURNED);
        verify(auditPublisher).publish(
                eq("PRESCRIPTION_RETURN"),
                eq("PRESCRIPTION"),
                eq("30000000-0000-4000-8000-000000000001"),
                eq("a0000000-0000-4000-8000-000000000001"),
                eq("30000000-0000-4000-8000-000000000001"),
                eq("pharmacist"),
                eq("PHARMACY_STAFF"),
                eq(Map.of("status", "RETURNED", "reason", "return-before-dispense")));
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
    void prescribeCreatesPrescriptionAndPublishesAuditEvents() {
        PharmacyRepository.Drug aspirin = drug("40000000-0000-4000-8000-000000000001", 20, 5);
        when(repository.drug("40000000-0000-4000-8000-000000000001")).thenReturn(aspirin);
        when(repository.findPrescription(any())).thenAnswer(invocation -> prescribed(invocation.getArgument(0), "trace-1", "FULL"));
        var request = new PharmacyController.CreatePrescriptionRequest(
                "00000000-0000-4000-8000-000000000001",
                "mr-1",
                "a0000000-0000-4000-8000-000000000001",
                "patient",
                "diagnosis",
                "trace-1",
                "AI_ACCEPTED",
                "revised",
                List.of(new PharmacyController.PrescriptionItemRequest(
                        "40000000-0000-4000-8000-000000000001",
                        2,
                        "100mg",
                        "oral",
                        "daily",
                        7,
                        null)));

        Prescription result = service.prescribe(request, "doctor-1");

        assertThat(result.aiAdoptionStatus()).isEqualTo("FULL");
        verify(repository).insertPrescription(any());
        verify(auditPublisher).publish(
                eq("PRESCRIPTION_CREATE"),
                eq("PRESCRIPTION"),
                eq(result.id()),
                eq(result.patientId()),
                eq(result.id()),
                eq("doctor-1"),
                eq("OUTPATIENT_DOCTOR"),
                eq(Map.of(
                        "appointmentId", result.appointmentId(),
                        "aiAdoptionStatus", "FULL",
                        "totalAmount", result.totalAmount())));
        verify(auditPublisher).publish(
                eq("AI_RESULT_CONFIRMED"),
                eq("PRESCRIPTION"),
                eq(result.id()),
                eq(result.patientId()),
                eq(result.id()),
                eq("doctor-1"),
                eq("OUTPATIENT_DOCTOR"),
                eq(Map.of("aiAssistanceId", "trace-1", "adoptionStatus", "FULL")));
    }

    @Test
    void prescribeRejectsUnsupportedAiStatus() {
        var request = new PharmacyController.CreatePrescriptionRequest(
                "appt-1",
                "mr-1",
                "patient-1",
                "patient",
                "diagnosis",
                null,
                "MAYBE",
                null,
                List.of(new PharmacyController.PrescriptionItemRequest(
                        "drug-1",
                        1,
                        "100mg",
                        "oral",
                        "daily",
                        7,
                        null)));
        when(repository.drug("drug-1")).thenReturn(drug("drug-1", 20, 5));

        assertThatThrownBy(() -> service.prescribe(request, "doctor-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported AI adoption status");
    }

    @Test
    void stockInAddsInventoryAndReturnsUpdatedDrug() {
        var drug = drug("drug-1", 35, 10);
        when(repository.drug("drug-1")).thenReturn(drug);

        PharmacyRepository.Drug updated = service.addStock(
                "drug-1",
                new PharmacyController.StockInRequest(5, "purchase"),
                "pharmacist");

        assertThat(updated.quantity()).isEqualTo(35);
        verify(repository).addStock("drug-1", 5, "pharmacist", "purchase");
    }

    @Test
    void stockInDefaultsBlankReasonAndAuditsOperatorAction() {
        var drug = drug("drug-1", 40, 10);
        when(repository.drug("drug-1")).thenReturn(drug);

        PharmacyRepository.Drug updated = service.addStock(
                "drug-1",
                new PharmacyController.StockInRequest(3, " "),
                "pharmacist");

        assertThat(updated).isSameAs(drug);
        verify(repository).addStock("drug-1", 3, "pharmacist", "stock-in");
        verify(auditPublisher).publish(
                eq("DRUG_STOCK_IN"),
                eq("DRUG"),
                eq("drug-1"),
                isNull(),
                isNull(),
                eq("pharmacist"),
                eq("PHARMACY_STAFF"),
                eq(Map.of("quantity", 3, "reason", "stock-in")));
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
    void findRejectsPatientWhenOwnershipCheckFails() {
        when(repository.findPrescription("pres-1")).thenReturn(prescription(PrescriptionStatus.WAITING_DISPENSE));
        when(patientAccessClient.owns("account-1", "a0000000-0000-4000-8000-000000000001")).thenReturn(false);

        assertThatThrownBy(() -> service.find("pres-1", "account-1", "PATIENT"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void findAuditsSuccessfulDetailView() {
        Prescription prescription = prescription(PrescriptionStatus.WAITING_DISPENSE);
        when(repository.findPrescription("pres-1")).thenReturn(prescription);

        Prescription result = service.find("pres-1", "staff-1", "PHARMACY_STAFF");

        assertThat(result).isSameAs(prescription);
        verify(auditPublisher).publish(
                eq("PRESCRIPTION_DETAIL_VIEW"),
                eq("PRESCRIPTION"),
                eq("30000000-0000-4000-8000-000000000001"),
                eq("a0000000-0000-4000-8000-000000000001"),
                eq("30000000-0000-4000-8000-000000000001"),
                eq("staff-1"),
                eq("PHARMACY_STAFF"),
                eq(Map.of("status", "WAITING_DISPENSE", "accessScope", "DETAIL")));
    }

    @Test
    void patientListRequiresBoundPatientProfile() {
        when(patientAccessClient.boundPatientId("account-1")).thenReturn(null);

        assertThatThrownBy(() -> service.list(null, null, null, "account-1", "PATIENT"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not bound");
    }

    @Test
    void patientPagedListRejectsAccessToAnotherPatientsPrescription() {
        when(patientAccessClient.owns("account-1", "patient-2")).thenReturn(false);

        assertThatThrownBy(() -> service.list("patient-2", null, null, null, null, 0, 20, "account-1", "PATIENT"))
                .isInstanceOf(AccessDeniedException.class);
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
                argThat(details ->
                        "LIST".equals(details.get("accessScope"))
                                && "OUTPATIENT_PAYMENT".equals(details.get("view"))
                                && List.of("CONFIRMED", "PENDING_PAYMENT").equals(details.get("statusFilter"))
                                && Integer.valueOf(1).equals(details.get("resultCount"))
                                && details.get("auditSummary") instanceof String));
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
                argThat(details ->
                        "LIST".equals(details.get("accessScope"))
                                && "PAYMENT_RECORD".equals(details.get("view"))
                                && Integer.valueOf(2).equals(details.get("relatedPrescriptionCount"))
                                && details.get("auditSummary") instanceof String));
    }

    @Test
    void createDrugReturnRejectsUnsupportedPrescriptionStatus() {
        when(repository.findPrescription("pres-1")).thenReturn(prescription(PrescriptionStatus.DISPENSED));

        assertThatThrownBy(() -> service.createDrugReturn(
                "pres-1",
                new PharmacyController.CreateDrugReturnRequest("approved", "template-1"),
                "doctor-1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createDrugReturnRejectsMissingDoctorOpinion() {
        when(repository.findPrescription("pres-1")).thenReturn(prescription(PrescriptionStatus.PENDING_PAYMENT));

        assertThatThrownBy(() -> service.createDrugReturn(
                "pres-1",
                new PharmacyController.CreateDrugReturnRequest(" ", "template-1"),
                "doctor-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("doctorOpinion");
    }

    @Test
    void createDrugReturnCreatesReturnOrderAndTransitionsPrescription() {
        Prescription prescription = prescription(PrescriptionStatus.PAID);
        DrugReturnOrder created = order("return-1", DrugReturnStatus.RETURN_PENDING_REFUND);
        when(repository.findPrescription("pres-1")).thenReturn(prescription);
        when(repository.createDrugReturn(
                prescription,
                "doctor-1",
                "approved",
                "template-1",
                DrugReturnStatus.RETURN_PENDING_REFUND)).thenReturn(created);
        when(repository.markReturnedBeforeDispense(
                "30000000-0000-4000-8000-000000000001",
                "doctor-1",
                "return-before-dispense-RT-1",
                PrescriptionStatus.RETURN_PENDING_REFUND)).thenReturn(true);
        when(repository.findDrugReturn("return-1")).thenReturn(created);

        DrugReturnOrder result = service.createDrugReturn(
                "pres-1",
                new PharmacyController.CreateDrugReturnRequest("approved", "template-1"),
                "doctor-1");

        assertThat(result).isSameAs(created);
        verify(auditPublisher).publish(
                eq("DRUG_RETURN_CREATE"),
                eq("DRUG_RETURN"),
                eq("return-1"),
                eq("a0000000-0000-4000-8000-000000000001"),
                eq("return-1"),
                eq("doctor-1"),
                eq("OUTPATIENT_DOCTOR"),
                eq(Map.of("status", "RETURN_PENDING_REFUND", "prescriptionId", "30000000-0000-4000-8000-000000000001")));
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

    @Test
    void completeDrugReturnReturnsExistingRefundedOrderWithoutFurtherChanges() {
        DrugReturnOrder refunded = order("return-1", DrugReturnStatus.RETURN_REFUNDED);
        when(repository.completeDrugReturn("return-1", "cashier-1", "refund-1")).thenReturn(false);
        when(repository.findDrugReturn("return-1")).thenReturn(refunded);

        DrugReturnOrder result = service.completeDrugReturn("return-1", "cashier-1", "refund-1");

        assertThat(result).isSameAs(refunded);
        verify(repository, never()).markReturnRefunded(any());
        verifyNoInteractions(auditPublisher);
    }

    @Test
    void completeDrugReturnThrowsWhenOrderIsNotWaitingForRefund() {
        when(repository.completeDrugReturn("return-1", "cashier-1", "refund-1")).thenReturn(false);
        when(repository.findDrugReturn("return-1")).thenReturn(order("return-1", DrugReturnStatus.RETURNED));

        assertThatThrownBy(() -> service.completeDrugReturn("return-1", "cashier-1", "refund-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not waiting for refund");
    }

    @Test
    void completeDrugReturnMarksPrescriptionRefundedAndAuditsSuccess() {
        DrugReturnOrder refunded = order("return-1", DrugReturnStatus.RETURN_REFUNDED);
        when(repository.completeDrugReturn("return-1", "cashier-1", "refund-1")).thenReturn(true);
        when(repository.findDrugReturn("return-1")).thenReturn(order("return-1", DrugReturnStatus.RETURN_PENDING_REFUND), refunded);
        when(repository.markReturnRefunded("30000000-0000-4000-8000-000000000001")).thenReturn(true);

        DrugReturnOrder result = service.completeDrugReturn("return-1", "cashier-1", "refund-1");

        assertThat(result).isSameAs(refunded);
        verify(auditPublisher).publish(
                eq("DRUG_RETURN_REFUND_COMPLETE"),
                eq("DRUG_RETURN"),
                eq("return-1"),
                eq("a0000000-0000-4000-8000-000000000001"),
                eq("return-1"),
                eq("cashier-1"),
                eq("CASHIER"),
                eq(Map.of("refundOrderId", "refund-1", "status", "RETURN_REFUNDED")));
    }

    @Test
    void confirmPaymentReturnsExistingWaitingDispenseWhenAlreadyConfirmed() {
        Prescription waiting = prescription(PrescriptionStatus.WAITING_DISPENSE);
        when(repository.markPaid("pres-1", "a0000000-0000-4000-8000-000000000001", "payment-1")).thenReturn(false);
        when(repository.findPrescription("pres-1")).thenReturn(waiting);

        Prescription result = service.confirmPayment("pres-1", "a0000000-0000-4000-8000-000000000001", "payment-1");

        assertThat(result).isSameAs(waiting);
    }

    @Test
    void confirmPaymentRejectsMismatchedPatientCallback() {
        when(repository.markPaid("pres-1", "patient-x", "payment-1")).thenReturn(false);
        when(repository.findPrescription("pres-1")).thenReturn(prescription(PrescriptionStatus.PENDING_PAYMENT));

        assertThatThrownBy(() -> service.confirmPayment("pres-1", "patient-x", "payment-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void confirmPaymentThrowsForUnsupportedPrescriptionStatus() {
        when(repository.markPaid("pres-1", "a0000000-0000-4000-8000-000000000001", "payment-1")).thenReturn(false);
        when(repository.findPrescription("pres-1")).thenReturn(prescription(PrescriptionStatus.RETURNED));

        assertThatThrownBy(() -> service.confirmPayment("pres-1", "a0000000-0000-4000-8000-000000000001", "payment-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot confirm payment");
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

    private Prescription prescribed(String id, String aiAssistanceId, String aiAdoptionStatus) {
        return new Prescription(
                id,
                "RX1",
                "00000000-0000-4000-8000-000000000001",
                "mr-1",
                "a0000000-0000-4000-8000-000000000001",
                "patient",
                "doctor-1",
                "diagnosis",
                PrescriptionStatus.PENDING_PAYMENT,
                BigDecimal.valueOf(20),
                null,
                aiAssistanceId,
                aiAdoptionStatus,
                "revised",
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
                        id,
                        "40000000-0000-4000-8000-000000000001",
                        "drug",
                        2,
                        "100mg",
                        "oral",
                        "daily",
                        7,
                        null,
                        BigDecimal.TEN,
                        BigDecimal.valueOf(20))));
    }

    private DrugReturnOrder order(String id, DrugReturnStatus status) {
        return new DrugReturnOrder(
                id,
                "RT-1",
                "30000000-0000-4000-8000-000000000001",
                "RX1",
                "a0000000-0000-4000-8000-000000000001",
                "patient",
                "doctor-1",
                "approved",
                "template-1",
                status,
                BigDecimal.TEN,
                null,
                null,
                "cashier-1",
                "refund-1",
                null,
                null,
                null,
                List.of());
    }

    private PharmacyRepository.Drug drug(String id, int quantity, int warningThreshold) {
        return new PharmacyRepository.Drug(
                id,
                "DRUG-1",
                "test drug",
                "10mg",
                "box",
                BigDecimal.TEN,
                "tablet",
                "room",
                quantity,
                warningThreshold);
    }
}
