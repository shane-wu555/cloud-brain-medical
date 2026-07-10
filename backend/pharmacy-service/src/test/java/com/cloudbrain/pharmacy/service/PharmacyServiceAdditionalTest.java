package com.cloudbrain.pharmacy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import com.cloudbrain.pharmacy.service.NotificationClient;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class PharmacyServiceAdditionalTest {
    @Mock
    PharmacyRepository repository;

    @Mock
    PatientAccessClient patientAccessClient;

    @Mock
    AuditPublisher auditPublisher;

    @Mock
    DrugSearchIndexService drugSearchIndexService;

    @Test
    void drugsUsesSearchResultsBeforeFallingBackToDatabase() {
        PharmacyService service = service();
        List<PharmacyRepository.Drug> matched = List.of(drug("drug-2"), drug("drug-1"));
        when(drugSearchIndexService.searchDrugIds("aspirin", "ROOM", 100))
                .thenReturn(Optional.of(List.of("drug-2", "drug-1")));
        when(repository.drugsByIds(List.of("drug-2", "drug-1"))).thenReturn(matched);

        List<PharmacyRepository.Drug> result = service.drugs("aspirin", "ROOM");

        assertThat(result).isSameAs(matched);
        verify(repository, never()).drugs(any(), any());
    }

    @Test
    void drugsSkipSearchIndexWhenKeywordIsBlank() {
        PharmacyService service = service();
        List<PharmacyRepository.Drug> drugs = List.of(drug("drug-1"));
        when(repository.drugs(" ", "ROOM")).thenReturn(drugs);

        List<PharmacyRepository.Drug> result = service.drugs(" ", "ROOM");

        assertThat(result).isSameAs(drugs);
        verify(drugSearchIndexService, never()).searchDrugIds(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void reindexDrugSearchDelegatesRepositorySnapshot() {
        PharmacyService service = service();
        List<PharmacyRepository.Drug> drugs = List.of(drug("drug-1"), drug("drug-2"));
        when(repository.drugs(null, null)).thenReturn(drugs);
        when(drugSearchIndexService.reindex(drugs)).thenReturn(2);

        int indexed = service.reindexDrugSearchIndex();

        assertThat(indexed).isEqualTo(2);
    }

    @Test
    void prescribeNormalizesAiStatusCreatesItemsAndPublishesAuditEvents() {
        PharmacyService service = service();
        AtomicReference<Prescription> inserted = new AtomicReference<>();
        when(repository.drug("drug-1")).thenReturn(drug("drug-1"));
        org.mockito.Mockito.doAnswer(invocation -> {
            inserted.set(invocation.getArgument(0));
            return null;
        }).when(repository).insertPrescription(any(Prescription.class));
        when(repository.findPrescription(any())).thenAnswer(invocation -> inserted.get());
        PharmacyController.CreatePrescriptionRequest request = new PharmacyController.CreatePrescriptionRequest(
                "appt-1",
                "mr-1",
                "patient-1",
                "Alice",
                "diagnosis",
                "ai-1",
                "AI_MODIFIED",
                "doctor adjusted dosage",
                List.of(new PharmacyController.PrescriptionItemRequest(
                        "drug-1",
                        2,
                        "100mg",
                        "oral",
                        "daily",
                        7,
                        "after meals")));

        Prescription created = service.prescribe(request, "doctor-1");

        assertThat(created.aiAdoptionStatus()).isEqualTo("PARTIAL");
        assertThat(created.totalAmount()).isEqualByComparingTo("20");
        assertThat(created.items()).singleElement().satisfies(item -> {
            assertThat(item.quantity()).isEqualTo(2);
            assertThat(item.amount()).isEqualByComparingTo("20");
            assertThat(item.note()).isEqualTo("after meals");
        });
        ArgumentCaptor<Prescription> prescriptionCaptor = ArgumentCaptor.forClass(Prescription.class);
        verify(repository).insertPrescription(prescriptionCaptor.capture());
        assertThat(prescriptionCaptor.getValue().prescriptionNo()).startsWith("RX");
        verify(auditPublisher).publish(
                eq("PRESCRIPTION_CREATE"),
                eq("PRESCRIPTION"),
                eq(created.id()),
                eq("patient-1"),
                eq(created.id()),
                eq("doctor-1"),
                eq("OUTPATIENT_DOCTOR"),
                eq(Map.of(
                        "appointmentId", "appt-1",
                        "aiAdoptionStatus", "PARTIAL",
                        "totalAmount", created.totalAmount())));
        verify(auditPublisher).publish(
                eq("AI_RESULT_CONFIRMED"),
                eq("PRESCRIPTION"),
                eq(created.id()),
                eq("patient-1"),
                eq(created.id()),
                eq("doctor-1"),
                eq("OUTPATIENT_DOCTOR"),
                eq(Map.of(
                        "aiAssistanceId", "ai-1",
                        "adoptionStatus", "PARTIAL")));
    }

    @Test
    void prescribeRejectsUnsupportedAiAdoptionStatus() {
        PharmacyService service = service();
        when(repository.drug("drug-1")).thenReturn(drug("drug-1"));
        PharmacyController.CreatePrescriptionRequest request = new PharmacyController.CreatePrescriptionRequest(
                "appt-1",
                "mr-1",
                "patient-1",
                "Alice",
                "diagnosis",
                "ai-1",
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

        assertThatThrownBy(() -> service.prescribe(request, "doctor-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported AI adoption status");
    }

    @Test
    void prescribeDefaultsBlankAiStatusToHumanOnlyWithoutAiAudit() {
        PharmacyService service = service();
        AtomicReference<Prescription> inserted = new AtomicReference<>();
        when(repository.drug("drug-1")).thenReturn(drug("drug-1"));
        org.mockito.Mockito.doAnswer(invocation -> {
            inserted.set(invocation.getArgument(0));
            return null;
        }).when(repository).insertPrescription(any(Prescription.class));
        when(repository.findPrescription(any())).thenAnswer(invocation -> inserted.get());
        PharmacyController.CreatePrescriptionRequest request = new PharmacyController.CreatePrescriptionRequest(
                "appt-2",
                "mr-2",
                "patient-1",
                "Alice",
                "diagnosis",
                null,
                " ",
                null,
                List.of(new PharmacyController.PrescriptionItemRequest(
                        "drug-1",
                        1,
                        "100mg",
                        "oral",
                        "daily",
                        5,
                        null)));

        Prescription created = service.prescribe(request, "doctor-1");

        assertThat(created.aiAdoptionStatus()).isEqualTo("HUMAN_ONLY");
        verify(auditPublisher, never()).publish(
                eq("AI_RESULT_CONFIRMED"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any());
    }

    @Test
    void prescribeRejectsEmptyItems() {
        PharmacyService service = service();
        PharmacyController.CreatePrescriptionRequest request = new PharmacyController.CreatePrescriptionRequest(
                "appt-3",
                "mr-3",
                "patient-1",
                "Alice",
                "diagnosis",
                null,
                null,
                null,
                List.of());

        assertThatThrownBy(() -> service.prescribe(request, "doctor-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one item");
    }

    @Test
    void prescribeRejectsMissingRequiredFields() {
        PharmacyService service = service();
        PharmacyController.CreatePrescriptionRequest request = new PharmacyController.CreatePrescriptionRequest(
                " ",
                "mr-3",
                "patient-1",
                "Alice",
                " ",
                null,
                null,
                null,
                List.of(new PharmacyController.PrescriptionItemRequest(
                        "drug-1",
                        1,
                        "100mg",
                        "oral",
                        "daily",
                        5,
                        null)));

        assertThatThrownBy(() -> service.prescribe(request, "doctor-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("appointmentId, patientId and diagnosis");
    }

    @Test
    void prescribeRejectsMissingMedicationInstructions() {
        PharmacyService service = service();
        PharmacyController.CreatePrescriptionRequest request = new PharmacyController.CreatePrescriptionRequest(
                "appt-4",
                "mr-4",
                "patient-1",
                "Alice",
                "diagnosis",
                null,
                null,
                null,
                List.of(new PharmacyController.PrescriptionItemRequest(
                        "drug-1",
                        1,
                        " ",
                        "oral",
                        "daily",
                        5,
                        null)));

        assertThatThrownBy(() -> service.prescribe(request, "doctor-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dosage, usage and frequency");
    }

    @Test
    void findRejectsPatientWithoutOwnership() {
        PharmacyService service = service();
        when(repository.findPrescription("pres-1")).thenReturn(prescription("pres-1", PrescriptionStatus.WAITING_DISPENSE));
        when(patientAccessClient.owns("account-1", "patient-1")).thenReturn(false);

        assertThatThrownBy(() -> service.find("pres-1", "account-1", "PATIENT"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("cannot access another patient's prescription");
    }

    @Test
    void findPublishesDetailAuditForAuthorizedAccess() {
        PharmacyService service = service();
        Prescription prescription = prescription("pres-1", PrescriptionStatus.WAITING_DISPENSE);
        when(repository.findPrescription("pres-1")).thenReturn(prescription);
        when(patientAccessClient.owns("account-1", "patient-1")).thenReturn(true);

        Prescription result = service.find("pres-1", "account-1", "PATIENT");

        assertThat(result).isSameAs(prescription);
        verify(auditPublisher).publish(
                eq("PRESCRIPTION_DETAIL_VIEW"),
                eq("PRESCRIPTION"),
                eq("pres-1"),
                eq("patient-1"),
                eq("pres-1"),
                eq("account-1"),
                eq("PATIENT"),
                eq(Map.of("status", "WAITING_DISPENSE", "accessScope", "DETAIL")));
    }

    @Test
    void pagedPrescriptionListUsesBoundPatientAndViewStatuses() {
        PharmacyService service = service();
        List<PrescriptionStatus> statuses = List.of(
                PrescriptionStatus.CONFIRMED,
                PrescriptionStatus.PENDING_PAYMENT,
                PrescriptionStatus.PAID,
                PrescriptionStatus.WAITING_DISPENSE);
        List<Prescription> prescriptions = List.of(prescription("pres-1", PrescriptionStatus.WAITING_DISPENSE));
        when(patientAccessClient.boundPatientId("account-1")).thenReturn("patient-1");
        when(patientAccessClient.owns("account-1", "patient-1")).thenReturn(true);
        when(repository.listByStatuses("patient-1", statuses, "Alice", "RX-1", 1, 20)).thenReturn(prescriptions);

        List<Prescription> result = service.list(
                null,
                null,
                "dispense_arrangement",
                "Alice",
                "RX-1",
                1,
                20,
                "account-1",
                "PATIENT");

        assertThat(result).isSameAs(prescriptions);
        verify(repository).listByStatuses("patient-1", statuses, "Alice", "RX-1", 1, 20);
        verify(auditPublisher).publish(
                eq("PRESCRIPTION_LIST_VIEW"),
                eq("PRESCRIPTION"),
                isNull(),
                eq("patient-1"),
                isNull(),
                eq("account-1"),
                eq("PATIENT"),
                eq(Map.of(
                        "accessScope", "LIST",
                        "view", "DISPENSE_ARRANGEMENT",
                        "auditSummary", "查看了待取药安排",
                        "statusFilter", List.of("CONFIRMED", "PENDING_PAYMENT", "PAID", "WAITING_DISPENSE"),
                        "resultCount", 1)));
    }

    @Test
    void createDrugReturnCreatesOrderAndUpdatesPrescriptionStatus() {
        PharmacyService service = service();
        Prescription prescription = prescription("pres-1", PrescriptionStatus.WAITING_DISPENSE);
        DrugReturnOrder order = order("return-1", DrugReturnStatus.RETURN_PENDING_REFUND);
        when(repository.findPrescription("pres-1")).thenReturn(prescription);
        when(repository.createDrugReturn(
                prescription,
                "doctor-1",
                "approved",
                "template-1",
                DrugReturnStatus.RETURN_PENDING_REFUND)).thenReturn(order);
        when(repository.markReturnedBeforeDispense(
                "pres-1",
                "doctor-1",
                "return-before-dispense-RT-1",
                PrescriptionStatus.RETURN_PENDING_REFUND)).thenReturn(true);
        when(repository.findDrugReturn("return-1")).thenReturn(order);

        DrugReturnOrder created = service.createDrugReturn(
                "pres-1",
                new PharmacyController.CreateDrugReturnRequest("approved", "template-1"),
                "doctor-1");

        assertThat(created).isSameAs(order);
        verify(auditPublisher).publish(
                eq("DRUG_RETURN_CREATE"),
                eq("DRUG_RETURN"),
                eq("return-1"),
                eq("patient-1"),
                eq("return-1"),
                eq("doctor-1"),
                eq("OUTPATIENT_DOCTOR"),
                eq(Map.of("status", "RETURN_PENDING_REFUND", "prescriptionId", "pres-1")));
    }

    @Test
    void createDrugReturnRejectsWhenPrescriptionChangesBeforeTransition() {
        PharmacyService service = service();
        Prescription prescription = prescription("pres-1", PrescriptionStatus.WAITING_DISPENSE);
        DrugReturnOrder order = order("return-1", DrugReturnStatus.RETURN_PENDING_REFUND);
        when(repository.findPrescription("pres-1")).thenReturn(prescription);
        when(repository.createDrugReturn(
                prescription,
                "doctor-1",
                "approved",
                "template-1",
                DrugReturnStatus.RETURN_PENDING_REFUND)).thenReturn(order);
        when(repository.markReturnedBeforeDispense(
                "pres-1",
                "doctor-1",
                "return-before-dispense-RT-1",
                PrescriptionStatus.RETURN_PENDING_REFUND)).thenReturn(false);

        assertThatThrownBy(() -> service.createDrugReturn(
                "pres-1",
                new PharmacyController.CreateDrugReturnRequest("approved", "template-1"),
                "doctor-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("status changed");
    }

    @Test
    void createDrugReturnReturnsCompletedStatusForUnpaidPrescription() {
        PharmacyService service = service();
        Prescription prescription = prescription("pres-1", PrescriptionStatus.PENDING_PAYMENT);
        DrugReturnOrder order = order("return-1", DrugReturnStatus.RETURNED);
        when(repository.findPrescription("pres-1")).thenReturn(prescription);
        when(repository.createDrugReturn(
                prescription,
                "doctor-1",
                "approved",
                "template-1",
                DrugReturnStatus.RETURNED)).thenReturn(order);
        when(repository.markReturnedBeforeDispense(
                "pres-1",
                "doctor-1",
                "return-before-dispense-RT-1",
                PrescriptionStatus.RETURNED)).thenReturn(true);
        when(repository.findDrugReturn("return-1")).thenReturn(order);

        DrugReturnOrder created = service.createDrugReturn(
                "pres-1",
                new PharmacyController.CreateDrugReturnRequest("approved", "template-1"),
                "doctor-1");

        assertThat(created.status()).isEqualTo(DrugReturnStatus.RETURNED);
    }

    @Test
    void pagedDrugReturnsUseBoundPatientScope() {
        PharmacyService service = service();
        List<DrugReturnOrder> orders = List.of(order("return-1", DrugReturnStatus.RETURN_PENDING_REFUND));
        when(patientAccessClient.boundPatientId("account-1")).thenReturn("patient-1");
        when(patientAccessClient.owns("account-1", "patient-1")).thenReturn(true);
        when(repository.listDrugReturns("patient-1", null, "Alice", "RX-1", "RT-1", 0, 10)).thenReturn(orders);

        List<DrugReturnOrder> result = service.drugReturns(
                null,
                null,
                "Alice",
                "RX-1",
                "RT-1",
                0,
                10,
                "account-1",
                "PATIENT");

        assertThat(result).isSameAs(orders);
        verify(repository).listDrugReturns("patient-1", null, "Alice", "RX-1", "RT-1", 0, 10);
        verify(auditPublisher).publish(
                eq("DRUG_RETURN_LIST_VIEW"),
                eq("DRUG_RETURN"),
                isNull(),
                eq("patient-1"),
                isNull(),
                eq("account-1"),
                eq("PATIENT"),
                eq(Map.of("accessScope", "LIST", "statusFilter", "ALL", "resultCount", 1)));
    }

    @Test
    void listUsesDispenseRecordViewStatuses() {
        PharmacyService service = service();
        List<PrescriptionStatus> statuses = List.of(
                PrescriptionStatus.DISPENSED,
                PrescriptionStatus.RETURNED,
                PrescriptionStatus.RETURN_PENDING_REFUND,
                PrescriptionStatus.RETURN_REFUNDED,
                PrescriptionStatus.CANCELLED);
        List<Prescription> prescriptions = List.of(prescription("pres-1", PrescriptionStatus.DISPENSED));
        when(repository.listByStatuses("patient-1", statuses)).thenReturn(prescriptions);

        List<Prescription> result = service.list("patient-1", null, "dispense_record", "cashier-1", "CASHIER");

        assertThat(result).isSameAs(prescriptions);
        verify(repository).listByStatuses("patient-1", statuses);
        verify(auditPublisher).publish(
                eq("PRESCRIPTION_LIST_VIEW"),
                eq("PRESCRIPTION"),
                isNull(),
                eq("patient-1"),
                isNull(),
                eq("cashier-1"),
                eq("CASHIER"),
                eq(Map.of(
                        "accessScope", "LIST",
                        "view", "DISPENSE_RECORD",
                        "auditSummary", "查看了取药退药记录",
                        "statusFilter", List.of("DISPENSED", "RETURNED", "RETURN_PENDING_REFUND", "RETURN_REFUNDED", "CANCELLED"),
                        "resultCount", 1)));
    }

    @Test
    void completeDrugReturnPublishesAuditAfterRefundCompletion() {
        PharmacyService service = service();
        DrugReturnOrder refunded = order("return-1", DrugReturnStatus.RETURN_REFUNDED);
        when(repository.completeDrugReturn("return-1", "cashier-1", "refund-1")).thenReturn(true);
        when(repository.findDrugReturn("return-1")).thenReturn(refunded);
        when(repository.markReturnRefunded("pres-1")).thenReturn(true);

        DrugReturnOrder result = service.completeDrugReturn("return-1", "cashier-1", "refund-1");

        assertThat(result).isSameAs(refunded);
        verify(auditPublisher).publish(
                eq("DRUG_RETURN_REFUND_COMPLETE"),
                eq("DRUG_RETURN"),
                eq("return-1"),
                eq("patient-1"),
                eq("return-1"),
                eq("cashier-1"),
                eq("CASHIER"),
                eq(Map.of("refundOrderId", "refund-1", "status", "RETURN_REFUNDED")));
    }

    @Test
    void completeDrugReturnRejectsUnexpectedPrescriptionStateWhenRefundStatusUpdateMisses() {
        PharmacyService service = service();
        DrugReturnOrder refunded = order("return-1", DrugReturnStatus.RETURN_REFUNDED);
        when(repository.completeDrugReturn("return-1", "cashier-1", "refund-1")).thenReturn(true);
        when(repository.findDrugReturn("return-1")).thenReturn(refunded);
        when(repository.markReturnRefunded("pres-1")).thenReturn(false);
        when(repository.findPrescription("pres-1")).thenReturn(prescription("pres-1", PrescriptionStatus.RETURNED));

        assertThatThrownBy(() -> service.completeDrugReturn("return-1", "cashier-1", "refund-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("waiting for refund");
    }

    @Test
    void completeDrugReturnAcceptsAlreadyRefundedPrescriptionState() {
        PharmacyService service = service();
        DrugReturnOrder refunded = order("return-1", DrugReturnStatus.RETURN_REFUNDED);
        when(repository.completeDrugReturn("return-1", "cashier-1", "refund-1")).thenReturn(true);
        when(repository.findDrugReturn("return-1")).thenReturn(refunded).thenReturn(refunded);
        when(repository.markReturnRefunded("pres-1")).thenReturn(false);
        when(repository.findPrescription("pres-1")).thenReturn(prescription("pres-1", PrescriptionStatus.RETURN_REFUNDED));

        DrugReturnOrder result = service.completeDrugReturn("return-1", "cashier-1", "refund-1");

        assertThat(result).isSameAs(refunded);
    }

    @Test
    void completeDrugReturnReturnsExistingRefundedOrderWhenAlreadyCompleted() {
        PharmacyService service = service();
        DrugReturnOrder refunded = order("return-1", DrugReturnStatus.RETURN_REFUNDED);
        when(repository.completeDrugReturn("return-1", "cashier-1", "refund-1")).thenReturn(false);
        when(repository.findDrugReturn("return-1")).thenReturn(refunded);

        DrugReturnOrder result = service.completeDrugReturn("return-1", "cashier-1", "refund-1");

        assertThat(result).isSameAs(refunded);
        verify(repository, never()).markReturnRefunded(any());
    }

    @Test
    void confirmPaymentSupportsUpdatedAndIdempotentStates() {
        PharmacyService service = service();
        Prescription waiting = prescription("pres-1", PrescriptionStatus.WAITING_DISPENSE);
        when(repository.markPaid("pres-1", "patient-1", "payment-1")).thenReturn(true);
        when(repository.findPrescription("pres-1")).thenReturn(waiting);

        Prescription updated = service.confirmPayment("pres-1", "patient-1", "payment-1");

        assertThat(updated).isSameAs(waiting);
        when(repository.markPaid("pres-1", "patient-1", "payment-1")).thenReturn(false);

        Prescription idempotent = service.confirmPayment("pres-1", "patient-1", "payment-1");

        assertThat(idempotent).isSameAs(waiting);
    }

    @Test
    void confirmPaymentAcceptsAlreadyDispensedPrescription() {
        PharmacyService service = service();
        Prescription dispensed = prescription("pres-1", PrescriptionStatus.DISPENSED);
        when(repository.markPaid("pres-1", "patient-1", "payment-1")).thenReturn(false);
        when(repository.findPrescription("pres-1")).thenReturn(dispensed);

        Prescription result = service.confirmPayment("pres-1", "patient-1", "payment-1");

        assertThat(result).isSameAs(dispensed);
    }

    @Test
    void pagedPrescriptionListUsesExplicitStatusFilterBeforeViewStatuses() {
        PharmacyService service = service();
        List<Prescription> prescriptions = List.of(prescription("pres-1", PrescriptionStatus.PAID));
        when(repository.list("patient-1", "PAID", "Alice", "RX-1", 0, 10)).thenReturn(prescriptions);

        List<Prescription> result = service.list(
                "patient-1",
                "PAID",
                "dispense_record",
                "Alice",
                "RX-1",
                0,
                10,
                "cashier-1",
                "CASHIER");

        assertThat(result).isSameAs(prescriptions);
        verify(repository).list("patient-1", "PAID", "Alice", "RX-1", 0, 10);
        verify(auditPublisher).publish(
                eq("PRESCRIPTION_LIST_VIEW"),
                eq("PRESCRIPTION"),
                isNull(),
                eq("patient-1"),
                isNull(),
                eq("cashier-1"),
                eq("CASHIER"),
                eq(Map.of(
                        "accessScope", "LIST",
                        "view", "DISPENSE_RECORD",
                        "auditSummary", "查看了取药退药记录",
                        "statusFilter", "PAID",
                        "resultCount", 1)));
    }

    @Test
    void dispenseRejectsConcurrentProcessing() {
        PharmacyService service = service();
        Prescription waiting = prescription("pres-1", PrescriptionStatus.WAITING_DISPENSE);
        when(repository.findPrescription("pres-1")).thenReturn(waiting);
        when(repository.markDispensed("pres-1", "pharmacist-1")).thenReturn(false);

        assertThatThrownBy(() -> service.dispense("pres-1", "pharmacist-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("another request");
    }

    @Test
    void returnDrugsRejectsConcurrentProcessing() {
        PharmacyService service = service();
        Prescription waiting = prescription("pres-1", PrescriptionStatus.WAITING_DISPENSE);
        when(repository.findPrescription("pres-1")).thenReturn(waiting);
        when(repository.markReturnedBeforeDispense(
                "pres-1",
                "pharmacist-1",
                "return-before-dispense",
                PrescriptionStatus.RETURN_PENDING_REFUND)).thenReturn(false);

        assertThatThrownBy(() -> service.returnDrugs("pres-1", "pharmacist-1", " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("another request");
    }

    @Test
    void dispenseDeductsStockMarksPrescriptionAndPublishesAudit() {
        PharmacyService service = service();
        Prescription waiting = prescription("pres-1", PrescriptionStatus.WAITING_DISPENSE);
        Prescription dispensed = prescription("pres-1", PrescriptionStatus.DISPENSED);
        when(repository.findPrescription("pres-1")).thenReturn(waiting).thenReturn(dispensed);
        when(repository.markDispensed("pres-1", "pharmacist-1")).thenReturn(true);

        Prescription result = service.dispense("pres-1", "pharmacist-1");

        assertThat(result).isSameAs(dispensed);
        verify(repository).deductStock("drug-1", "pres-1", 2, "pharmacist-1");
        verify(auditPublisher).publish(
                eq("PRESCRIPTION_DISPENSE"),
                eq("PRESCRIPTION"),
                eq("pres-1"),
                eq("patient-1"),
                eq("pres-1"),
                eq("pharmacist-1"),
                eq("PHARMACY_STAFF"),
                eq(Map.of("status", "DISPENSED")));
    }

    private PharmacyService service() {
        return new PharmacyService(repository, patientAccessClient, auditPublisher, drugSearchIndexService, null);
    }

    private PharmacyRepository.Drug drug(String id) {
        return new PharmacyRepository.Drug(
                id,
                "DRUG-1",
                "Aspirin",
                "100mg",
                "box",
                BigDecimal.TEN,
                "tablet",
                "ROOM",
                50,
                10);
    }

    private Prescription prescription(String id, PrescriptionStatus status) {
        return new Prescription(
                id,
                "RX-1",
                "appt-1",
                "mr-1",
                "patient-1",
                "Alice",
                "doctor-1",
                "diagnosis",
                status,
                BigDecimal.valueOf(20),
                "payment-1",
                "ai-1",
                "PARTIAL",
                "doctor adjusted dosage",
                LocalDateTime.of(2026, 7, 9, 10, 0),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(new PrescriptionItem(
                        "item-1",
                        id,
                        "drug-1",
                        "Aspirin",
                        2,
                        "100mg",
                        "oral",
                        "daily",
                        7,
                        "after meals",
                        BigDecimal.TEN,
                        BigDecimal.valueOf(20))));
    }

    private DrugReturnOrder order(String id, DrugReturnStatus status) {
        return new DrugReturnOrder(
                id,
                "RT-1",
                "pres-1",
                "RX-1",
                "patient-1",
                "Alice",
                "doctor-1",
                "approved",
                "template-1",
                status,
                BigDecimal.valueOf(20),
                null,
                null,
                "cashier-1",
                "refund-1",
                LocalDateTime.of(2026, 7, 9, 10, 0),
                null,
                null,
                List.of());
    }
}
