package com.cloudbrain.pharmacy.service;

import com.cloudbrain.pharmacy.controller.PharmacyController;
import com.cloudbrain.pharmacy.entity.Prescription;
import com.cloudbrain.pharmacy.entity.PrescriptionItem;
import com.cloudbrain.pharmacy.entity.PrescriptionStatus;
import com.cloudbrain.pharmacy.repository.PharmacyRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PharmacyService {
    private final PharmacyRepository repository;

    public PharmacyService(PharmacyRepository repository) {
        this.repository = repository;
    }

    public List<PharmacyRepository.Drug> drugs(String keyword) {
        return repository.drugs(keyword);
    }

    @Transactional
    public Prescription prescribe(PharmacyController.CreatePrescriptionRequest request, String doctorId) {
        if (blank(request.appointmentId()) || blank(request.patientId()) || blank(request.diagnosis())) {
            throw new IllegalArgumentException("appointmentId、patientId 和 diagnosis 不能为空");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("处方至少需要一条药品明细");
        }
        if ("AI_ACCEPTED".equals(request.aiAdoptionStatus()) && blank(request.aiAssistanceId())) {
            throw new IllegalArgumentException("采纳 AI 建议必须关联 aiAssistanceId");
        }
        String id = "rx-" + UUID.randomUUID();
        List<PrescriptionItem> items = request.items().stream()
                .map(requestItem -> item(id, requestItem))
                .toList();
        BigDecimal total = items.stream().map(PrescriptionItem::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Prescription prescription = new Prescription(id, "RX" + System.currentTimeMillis(),
                request.appointmentId(), request.medicalRecordId(), request.patientId(), request.patientName(),
                doctorId, request.diagnosis(), PrescriptionStatus.PENDING_PAYMENT, total,
                null, request.aiAssistanceId(), normalizeAiStatus(request.aiAdoptionStatus()),
                request.aiRevisionNote(), null, null, null, null, null, null, null, null, items);
        repository.insertPrescription(prescription);
        return repository.findPrescription(id);
    }

    public Prescription find(String id, String patientId, String role) {
        Prescription prescription = repository.findPrescription(id);
        if ("PATIENT".equals(role) && !prescription.patientId().equals(patientId)) {
            throw new org.springframework.security.access.AccessDeniedException("患者只能查看自己的处方");
        }
        return prescription;
    }

    public List<Prescription> list(String patientId, String status, String requesterId, String role) {
        String scopedPatientId = "PATIENT".equals(role) ? requesterId : patientId;
        return repository.list(scopedPatientId, status);
    }

    @Transactional
    public Prescription confirmPayment(String id, String patientId, String paymentOrderId) {
        boolean updated = repository.markPaid(id, patientId, paymentOrderId);
        if (!updated) {
            Prescription prescription = repository.findPrescription(id);
            if (!prescription.patientId().equals(patientId)) throw new IllegalArgumentException("处方患者不匹配");
            if (prescription.status() == PrescriptionStatus.WAITING_DISPENSE || prescription.status() == PrescriptionStatus.DISPENSED) {
                return prescription;
            }
            throw new IllegalStateException("处方当前状态不能确认缴费");
        }
        return repository.findPrescription(id);
    }

    @Transactional
    public Prescription dispense(String id, String operatorId) {
        Prescription prescription = repository.findPrescription(id);
        if (prescription.status() != PrescriptionStatus.WAITING_DISPENSE) {
            throw new IllegalStateException("只有已缴费且待发药处方可以发药");
        }
        for (PrescriptionItem item : prescription.items()) {
            repository.deductStock(item.drugId(), id, item.quantity(), operatorId);
        }
        if (!repository.markDispensed(id, operatorId)) {
            throw new IllegalStateException("处方已被其他窗口处理，请刷新后重试");
        }
        return repository.findPrescription(id);
    }

    @Transactional
    public Prescription returnDrugs(String id, String operatorId, String reason) {
        Prescription prescription = repository.findPrescription(id);
        if (prescription.status() != PrescriptionStatus.DISPENSED) {
            throw new IllegalStateException("只有已发药处方可以退药");
        }
        String returnReason = blank(reason) ? "药房退药" : reason;
        for (PrescriptionItem item : prescription.items()) {
            repository.restoreStock(item.drugId(), id, item.quantity(), operatorId, returnReason);
        }
        if (!repository.markReturned(id, operatorId, returnReason)) {
            throw new IllegalStateException("处方已被其他窗口处理，请刷新后重试");
        }
        return repository.findPrescription(id);
    }

    private PrescriptionItem item(String prescriptionId, PharmacyController.PrescriptionItemRequest request) {
        if (request.quantity() <= 0 || request.days() <= 0) throw new IllegalArgumentException("药品数量和天数必须大于 0");
        if (blank(request.dosage()) || blank(request.usage()) || blank(request.frequency())) {
            throw new IllegalArgumentException("剂量、用法和频次不能为空");
        }
        PharmacyRepository.Drug drug = repository.drug(request.drugId());
        BigDecimal amount = drug.unitPrice().multiply(BigDecimal.valueOf(request.quantity()));
        return new PrescriptionItem("rx-item-" + UUID.randomUUID(), prescriptionId, drug.id(), drug.drugName(),
                request.quantity(), request.dosage(), request.usage(), request.frequency(), request.days(),
                request.note(), drug.unitPrice(), amount);
    }

    private String normalizeAiStatus(String value) {
        if (blank(value)) return "HUMAN_ONLY";
        if (!List.of("AI_ACCEPTED", "AI_MODIFIED", "AI_REJECTED", "HUMAN_ONLY").contains(value)) {
            throw new IllegalArgumentException("AI 建议处理状态不合法");
        }
        return value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
