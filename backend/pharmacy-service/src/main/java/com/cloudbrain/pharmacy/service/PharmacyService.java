package com.cloudbrain.pharmacy.service;

import com.cloudbrain.pharmacy.audit.AuditPublisher;
import com.cloudbrain.pharmacy.controller.PharmacyController;
import com.cloudbrain.pharmacy.entity.DrugReturnOrder;
import com.cloudbrain.pharmacy.entity.DrugReturnStatus;
import com.cloudbrain.pharmacy.entity.Prescription;
import com.cloudbrain.pharmacy.entity.PrescriptionItem;
import com.cloudbrain.pharmacy.entity.PrescriptionStatus;
import com.cloudbrain.pharmacy.repository.PharmacyRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PharmacyService {
    private final PharmacyRepository repository;
    private final PatientAccessClient patientAccessClient;
    private final AuditPublisher auditPublisher;
    private final DrugSearchIndexService drugSearchIndexService;
    private final NotificationClient notificationClient;

    public PharmacyService(
            PharmacyRepository repository,
            PatientAccessClient patientAccessClient,
            AuditPublisher auditPublisher,
            DrugSearchIndexService drugSearchIndexService,
            NotificationClient notificationClient) {
        this.repository = repository;
        this.patientAccessClient = patientAccessClient;
        this.auditPublisher = auditPublisher;
        this.drugSearchIndexService = drugSearchIndexService;
        this.notificationClient = notificationClient;
    }

    public List<PharmacyRepository.Drug> drugs(String keyword, String storageCondition) {
        if (!blank(keyword)) {
            var matchedIds = drugSearchIndexService.searchDrugIds(keyword, storageCondition, 100);
            if (matchedIds.isPresent() && !matchedIds.get().isEmpty()) {
                return repository.drugsByIds(matchedIds.get());
            }
        }
        return repository.drugs(keyword, storageCondition);
    }

    public int reindexDrugSearchIndex() {
        return drugSearchIndexService.reindex(repository.drugs(null, null));
    }

    @Transactional
    @CacheEvict(cacheNames = "drugs", allEntries = true)
    public PharmacyRepository.Drug addStock(String drugId, PharmacyController.StockInRequest request, String operatorId) {
        if (request.quantity() <= 0) {
            throw new IllegalArgumentException("Stock-in quantity must be positive");
        }
        String reason = blank(request.reason()) ? "stock-in" : request.reason().trim();
        repository.addStock(drugId, request.quantity(), operatorId, reason);
        PharmacyRepository.Drug drug = repository.drug(drugId);
        drugSearchIndexService.index(drug);
        auditPublisher.publish(
                "DRUG_STOCK_IN",
                "DRUG",
                drugId,
                null,
                null,
                operatorId,
                "PHARMACY_STAFF",
                Map.of("quantity", request.quantity(), "reason", reason));
        return drug;
    }

    @Transactional
    public Prescription prescribe(PharmacyController.CreatePrescriptionRequest request, String doctorId) {
        if (blank(request.appointmentId()) || blank(request.patientId()) || blank(request.diagnosis())) {
            throw new IllegalArgumentException("appointmentId, patientId and diagnosis are required");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("Prescription must contain at least one item");
        }
        if ("AI_ACCEPTED".equals(request.aiAdoptionStatus()) && blank(request.aiAssistanceId())) {
            throw new IllegalArgumentException("Accepted AI suggestions must include aiAssistanceId");
        }

        String id = UUID.randomUUID().toString();
        List<PrescriptionItem> items = request.items().stream()
                .map(requestItem -> item(id, requestItem))
                .toList();
        BigDecimal total = items.stream().map(PrescriptionItem::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Prescription prescription = new Prescription(
                id,
                "RX" + System.currentTimeMillis(),
                request.appointmentId(),
                request.medicalRecordId(),
                request.patientId(),
                request.patientName(),
                doctorId,
                request.diagnosis(),
                PrescriptionStatus.PENDING_PAYMENT,
                total,
                null,
                request.aiAssistanceId(),
                normalizeAiStatus(request.aiAdoptionStatus()),
                request.aiRevisionNote(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                items);
        repository.insertPrescription(prescription);
        Prescription created = repository.findPrescription(id);
        auditPublisher.publish(
                "PRESCRIPTION_CREATE",
                "PRESCRIPTION",
                created.id(),
                created.patientId(),
                created.id(),
                doctorId,
                "OUTPATIENT_DOCTOR",
                Map.of(
                        "appointmentId", created.appointmentId(),
                        "aiAdoptionStatus", created.aiAdoptionStatus(),
                        "totalAmount", created.totalAmount()));
        if (!blank(created.aiAssistanceId())) {
            auditPublisher.publish(
                    "AI_RESULT_CONFIRMED",
                    "PRESCRIPTION",
                    created.id(),
                    created.patientId(),
                    created.id(),
                    doctorId,
                    "OUTPATIENT_DOCTOR",
                    Map.of(
                            "aiAssistanceId", created.aiAssistanceId(),
                            "adoptionStatus", created.aiAdoptionStatus()));
        }
        try {
            notificationClient.notify(created.patientId(), "PENDING_PAYMENT",
                    "处方已开立，请缴费", null,
                    "PRESCRIPTION", created.id());
        } catch (Exception ignored) { /* notification failure must not fail the transaction */ }
        return created;
    }

    public Prescription find(String id, String requesterId, String role) {
        Prescription prescription = repository.findPrescription(id);
        if ("PATIENT".equals(role) && !patientAccessClient.owns(requesterId, prescription.patientId())) {
            throw new AccessDeniedException("Patient cannot access another patient's prescription");
        }
        auditPublisher.publish(
                "PRESCRIPTION_DETAIL_VIEW",
                "PRESCRIPTION",
                prescription.id(),
                prescription.patientId(),
                prescription.id(),
                requesterId,
                role,
                Map.of(
                        "status", prescription.status().name(),
                        "accessScope", "DETAIL"));
        return prescription;
    }

    public List<Prescription> list(String patientId, String status, String view, String requesterId, String role) {
        String scopedPatientId = patientId;
        if ("PATIENT".equals(role)) {
            if (blank(scopedPatientId)) {
                scopedPatientId = patientAccessClient.boundPatientId(requesterId);
            }
            if (blank(scopedPatientId)) {
                throw new AccessDeniedException("Patient account is not bound to a patient profile");
            }
            if (!patientAccessClient.owns(requesterId, scopedPatientId)) {
                throw new AccessDeniedException("Patient cannot access another patient's prescriptions");
            }
        }
        String normalizedView = normalizeView(view);
        List<PrescriptionStatus> viewStatuses = prescriptionStatusesForView(normalizedView);
        List<Prescription> prescriptions = blank(status) && viewStatuses != null
                ? repository.listByStatuses(scopedPatientId, viewStatuses)
                : repository.list(scopedPatientId, status);
        auditPublisher.publish(
                "PRESCRIPTION_LIST_VIEW",
                "PRESCRIPTION",
                null,
                scopedPatientId,
                null,
                requesterId,
                role,
                prescriptionListAuditDetails(status, normalizedView, viewStatuses, prescriptions.size()));
        return prescriptions;
    }

    public List<Prescription> list(String patientId, String status, String view, String patientName,
            String prescriptionNo, int page, int size, String requesterId, String role) {
        String scopedPatientId = patientId;
        if ("PATIENT".equals(role)) {
            if (blank(scopedPatientId)) {
                scopedPatientId = patientAccessClient.boundPatientId(requesterId);
            }
            if (blank(scopedPatientId)) {
                throw new AccessDeniedException("Patient account is not bound to a patient profile");
            }
            if (!patientAccessClient.owns(requesterId, scopedPatientId)) {
                throw new AccessDeniedException("Patient cannot access another patient's prescriptions");
            }
        }
        String normalizedView = normalizeView(view);
        List<PrescriptionStatus> viewStatuses = prescriptionStatusesForView(normalizedView);
        List<Prescription> prescriptions = blank(status) && viewStatuses != null
                ? repository.listByStatuses(scopedPatientId, viewStatuses, patientName, prescriptionNo, page, size)
                : repository.list(scopedPatientId, status, patientName, prescriptionNo, page, size);
        auditPublisher.publish(
                "PRESCRIPTION_LIST_VIEW",
                "PRESCRIPTION",
                null,
                scopedPatientId,
                null,
                requesterId,
                role,
                prescriptionListAuditDetails(status, normalizedView, viewStatuses, prescriptions.size()));
        return prescriptions;
    }

    @Transactional
    public DrugReturnOrder createDrugReturn(
            String prescriptionId,
            PharmacyController.CreateDrugReturnRequest request,
            String doctorId) {
        Prescription prescription = repository.findPrescription(prescriptionId);
        if (!canReturnBeforeDispense(prescription.status())) {
            throw new IllegalStateException("Prescription cannot be returned in current status");
        }
        if (blank(request.doctorOpinion())) {
            throw new IllegalArgumentException("doctorOpinion is required");
        }

        PrescriptionStatus targetStatus = returnStatusFor(prescription.status());
        DrugReturnStatus returnStatus = targetStatus == PrescriptionStatus.RETURN_PENDING_REFUND
                ? DrugReturnStatus.RETURN_PENDING_REFUND
                : DrugReturnStatus.RETURNED;
        DrugReturnOrder order = repository.createDrugReturn(
                prescription,
                doctorId,
                request.doctorOpinion(),
                request.opinionTemplate(),
                returnStatus);
        if (!repository.markReturnedBeforeDispense(
                prescription.id(),
                doctorId,
                "return-before-dispense-" + order.returnNo(),
                targetStatus)) {
            throw new IllegalStateException("Prescription status changed before return was created");
        }
        DrugReturnOrder created = repository.findDrugReturn(order.id());
        auditPublisher.publish(
                "DRUG_RETURN_CREATE",
                "DRUG_RETURN",
                created.id(),
                created.patientId(),
                created.id(),
                doctorId,
                "OUTPATIENT_DOCTOR",
                Map.of("status", created.status().name(), "prescriptionId", created.prescriptionId()));
        return created;
    }

    public List<DrugReturnOrder> drugReturns(String patientId, String status, String requesterId, String role) {
        String scopedPatientId = patientId;
        if ("PATIENT".equals(role)) {
            if (blank(scopedPatientId)) {
                scopedPatientId = patientAccessClient.boundPatientId(requesterId);
            }
            if (blank(scopedPatientId)) {
                throw new AccessDeniedException("Patient account is not bound to a patient profile");
            }
            if (!patientAccessClient.owns(requesterId, scopedPatientId)) {
                throw new AccessDeniedException("Patient cannot access another patient's return orders");
            }
        }
        List<DrugReturnOrder> orders = repository.listDrugReturns(scopedPatientId, status);
        auditPublisher.publish(
                "DRUG_RETURN_LIST_VIEW",
                "DRUG_RETURN",
                null,
                scopedPatientId,
                null,
                requesterId,
                role,
                Map.of(
                        "accessScope", "LIST",
                        "statusFilter", blank(status) ? "ALL" : status.trim(),
                        "resultCount", orders.size()));
        return orders;
    }

    public List<DrugReturnOrder> drugReturns(String patientId, String status, String patientName,
            String prescriptionNo, String returnNo, int page, int size, String requesterId, String role) {
        String scopedPatientId = patientId;
        if ("PATIENT".equals(role)) {
            if (blank(scopedPatientId)) {
                scopedPatientId = patientAccessClient.boundPatientId(requesterId);
            }
            if (blank(scopedPatientId)) {
                throw new AccessDeniedException("Patient account is not bound to a patient profile");
            }
            if (!patientAccessClient.owns(requesterId, scopedPatientId)) {
                throw new AccessDeniedException("Patient cannot access another patient's return orders");
            }
        }
        List<DrugReturnOrder> orders = repository.listDrugReturns(
                scopedPatientId, status, patientName, prescriptionNo, returnNo, page, size);
        auditPublisher.publish(
                "DRUG_RETURN_LIST_VIEW",
                "DRUG_RETURN",
                null,
                scopedPatientId,
                null,
                requesterId,
                role,
                Map.of(
                        "accessScope", "LIST",
                        "statusFilter", blank(status) ? "ALL" : status.trim(),
                        "resultCount", orders.size()));
        return orders;
    }

    @Transactional
    public DrugReturnOrder completeDrugReturn(String id, String cashierId, String refundOrderId) {
        if (!repository.completeDrugReturn(id, cashierId, refundOrderId)) {
            DrugReturnOrder order = repository.findDrugReturn(id);
            if (order.status() == DrugReturnStatus.RETURN_REFUNDED) {
                return order;
            }
            throw new IllegalStateException("Drug return is not waiting for refund");
        }
        DrugReturnOrder order = repository.findDrugReturn(id);
        if (!repository.markReturnRefunded(order.prescriptionId())) {
            Prescription prescription = repository.findPrescription(order.prescriptionId());
            if (prescription.status() != PrescriptionStatus.RETURN_REFUNDED) {
                throw new IllegalStateException("Prescription is not waiting for refund");
            }
        }
        DrugReturnOrder completed = repository.findDrugReturn(id);
        auditPublisher.publish(
                "DRUG_RETURN_REFUND_COMPLETE",
                "DRUG_RETURN",
                completed.id(),
                completed.patientId(),
                completed.id(),
                cashierId,
                "CASHIER",
                Map.of("refundOrderId", refundOrderId, "status", completed.status().name()));
        try {
            notificationClient.notify(completed.patientId(), "DRUG_RETURN_REFUNDED",
                    "退药退款已完成", null,
                    "DRUG_RETURN", completed.id());
        } catch (Exception ignored) { /* notification failure must not fail the transaction */ }
        return completed;
    }

    @Transactional
    public Prescription confirmPayment(String id, String patientId, String paymentOrderId) {
        boolean updated = repository.markPaid(id, patientId, paymentOrderId);
        if (!updated) {
            Prescription prescription = repository.findPrescription(id);
            if (!prescription.patientId().equals(patientId)) {
                throw new IllegalArgumentException("Prescription patient does not match payment callback");
            }
            if (prescription.status() == PrescriptionStatus.WAITING_DISPENSE
                    || prescription.status() == PrescriptionStatus.DISPENSED) {
                markPendingPaymentRead(prescription);
                if (prescription.status() == PrescriptionStatus.DISPENSED) {
                    markDispenseArrangementRead(prescription);
                }
                return prescription;
            }
            throw new IllegalStateException("Prescription cannot confirm payment in current status");
        }
        Prescription paid = repository.findPrescription(id);
        try {
            markPendingPaymentRead(paid);
            notificationClient.notify(paid.patientId(), "DISPENSE_ARRANGEMENT",
                    "处方缴费成功，请前往药房取药", null,
                    "PRESCRIPTION", paid.id());
        } catch (Exception ignored) { /* notification failure must not fail the transaction */ }
        return paid;
    }

    @Transactional
    @CacheEvict(cacheNames = "drugs", allEntries = true)
    public Prescription dispense(String id, String operatorId) {
        Prescription prescription = repository.findPrescription(id);
        if (prescription.status() != PrescriptionStatus.WAITING_DISPENSE) {
            throw new IllegalStateException("Only waiting prescriptions can be dispensed");
        }
        for (PrescriptionItem item : prescription.items()) {
            repository.deductStock(item.drugId(), id, item.quantity(), operatorId);
        }
        if (!repository.markDispensed(id, operatorId)) {
            throw new IllegalStateException("Prescription was processed by another request");
        }
        Prescription dispensed = repository.findPrescription(id);
        auditPublisher.publish(
                "PRESCRIPTION_DISPENSE",
                "PRESCRIPTION",
                dispensed.id(),
                dispensed.patientId(),
                dispensed.id(),
                operatorId,
                "PHARMACY_STAFF",
                Map.of("status", dispensed.status().name()));
        markDispenseArrangementRead(dispensed);
        return dispensed;
    }

    @Transactional
    public Prescription returnDrugs(String id, String operatorId, String reason) {
        Prescription prescription = repository.findPrescription(id);
        if (!canReturnBeforeDispense(prescription.status())) {
            throw new IllegalStateException("Prescription cannot be returned in current status");
        }
        String returnReason = blank(reason) ? "return-before-dispense" : reason;
        if (!repository.markReturnedBeforeDispense(
                id,
                operatorId,
                returnReason,
                returnStatusFor(prescription.status()))) {
            throw new IllegalStateException("Prescription was processed by another request");
        }
        Prescription returned = repository.findPrescription(id);
        auditPublisher.publish(
                "PRESCRIPTION_RETURN",
                "PRESCRIPTION",
                returned.id(),
                returned.patientId(),
                returned.id(),
                operatorId,
                "PHARMACY_STAFF",
                Map.of("status", returned.status().name(), "reason", returnReason));
        markDispenseArrangementRead(returned);
        return returned;
    }

    private void markPendingPaymentRead(Prescription prescription) {
        try {
            notificationClient.markReferenceRead(prescription.patientId(), "PENDING_PAYMENT",
                    "PRESCRIPTION", prescription.id());
        } catch (Exception ignored) { /* notification failure must not fail the transaction */ }
    }

    private void markDispenseArrangementRead(Prescription prescription) {
        try {
            notificationClient.markReferenceRead(prescription.patientId(), "DISPENSE_ARRANGEMENT",
                    "PRESCRIPTION", prescription.id());
        } catch (Exception ignored) { /* notification failure must not fail the transaction */ }
    }

    private PrescriptionItem item(String prescriptionId, PharmacyController.PrescriptionItemRequest request) {
        if (request.quantity() <= 0 || request.days() <= 0) {
            throw new IllegalArgumentException("Prescription quantity and days must be positive");
        }
        if (blank(request.dosage()) || blank(request.usage()) || blank(request.frequency())) {
            throw new IllegalArgumentException("dosage, usage and frequency are required");
        }
        PharmacyRepository.Drug drug = repository.drug(request.drugId());
        BigDecimal amount = drug.unitPrice().multiply(BigDecimal.valueOf(request.quantity()));
        return new PrescriptionItem(
                UUID.randomUUID().toString(),
                prescriptionId,
                drug.id(),
                drug.drugName(),
                request.quantity(),
                request.dosage(),
                request.usage(),
                request.frequency(),
                request.days(),
                request.note(),
                drug.unitPrice(),
                amount);
    }

    private String normalizeAiStatus(String value) {
        if (blank(value)) {
            return "HUMAN_ONLY";
        }
        if (List.of("AI_ACCEPTED", "FULL").contains(value)) {
            return "FULL";
        }
        if (List.of("AI_MODIFIED", "PARTIAL").contains(value)) {
            return "PARTIAL";
        }
        if (List.of("AI_REJECTED", "REJECTED").contains(value)) {
            return "REJECTED";
        }
        if ("HUMAN_ONLY".equals(value)) {
            return "HUMAN_ONLY";
        }
        throw new IllegalArgumentException("Unsupported AI adoption status");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalizeView(String value) {
        return blank(value) ? "" : value.trim().toUpperCase();
    }

    private static List<PrescriptionStatus> prescriptionStatusesForView(String view) {
        return switch (view) {
            case "OUTPATIENT_PAYMENT" -> List.of(PrescriptionStatus.CONFIRMED, PrescriptionStatus.PENDING_PAYMENT);
            case "DISPENSE_ARRANGEMENT" -> List.of(
                    PrescriptionStatus.CONFIRMED,
                    PrescriptionStatus.PENDING_PAYMENT,
                    PrescriptionStatus.PAID,
                    PrescriptionStatus.WAITING_DISPENSE);
            case "DISPENSE_RECORD" -> List.of(
                    PrescriptionStatus.DISPENSED,
                    PrescriptionStatus.RETURNED,
                    PrescriptionStatus.RETURN_PENDING_REFUND,
                    PrescriptionStatus.RETURN_REFUNDED,
                    PrescriptionStatus.CANCELLED);
            default -> null;
        };
    }

    private static Map<String, Object> prescriptionListAuditDetails(
            String status,
            String view,
            List<PrescriptionStatus> viewStatuses,
            int resultCount) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("accessScope", "LIST");
        if (!view.isBlank()) {
            details.put("view", view);
            details.put("auditSummary", prescriptionListAuditSummary(view));
        }
        if ("PAYMENT_RECORD".equals(view)) {
            details.put("relatedPrescriptionCount", resultCount);
            return details;
        }
        if (!blank(status)) {
            details.put("statusFilter", status.trim());
        } else if (viewStatuses != null) {
            details.put("statusFilter", viewStatuses.stream().map(PrescriptionStatus::name).toList());
        } else {
            details.put("statusFilter", "ALL");
        }
        details.put("resultCount", resultCount);
        return details;
    }

    private static String prescriptionListAuditSummary(String view) {
        return switch (view) {
            case "OUTPATIENT_PAYMENT" -> "查看了门诊缴费项目（含处方信息）";
            case "PAYMENT_RECORD" -> "查看了缴费退费记录（含处方信息）";
            case "DISPENSE_ARRANGEMENT" -> "查看了待取药安排";
            case "DISPENSE_RECORD" -> "查看了取药退药记录";
            default -> "查看了处方列表";
        };
    }

    private static boolean canReturnBeforeDispense(PrescriptionStatus status) {
        return status == PrescriptionStatus.PENDING_PAYMENT
                || status == PrescriptionStatus.CONFIRMED
                || status == PrescriptionStatus.PAID
                || status == PrescriptionStatus.WAITING_DISPENSE;
    }

    private static PrescriptionStatus returnStatusFor(PrescriptionStatus status) {
        if (status == PrescriptionStatus.PENDING_PAYMENT || status == PrescriptionStatus.CONFIRMED) {
            return PrescriptionStatus.RETURNED;
        }
        if (status == PrescriptionStatus.PAID || status == PrescriptionStatus.WAITING_DISPENSE) {
            return PrescriptionStatus.RETURN_PENDING_REFUND;
        }
        throw new IllegalStateException("Prescription cannot be returned in current status");
    }
}
