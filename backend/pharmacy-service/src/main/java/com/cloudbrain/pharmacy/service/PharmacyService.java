package com.cloudbrain.pharmacy.service;

import com.cloudbrain.pharmacy.controller.PharmacyController;
import com.cloudbrain.pharmacy.entity.DrugReturnOrder;
import com.cloudbrain.pharmacy.entity.DrugReturnStatus;
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
    private final PatientAccessClient patientAccessClient;

    public PharmacyService(PharmacyRepository repository, PatientAccessClient patientAccessClient) {
        this.repository = repository;
        this.patientAccessClient = patientAccessClient;
    }

    public List<PharmacyRepository.Drug> drugs(String keyword, String storageCondition) {
        return repository.drugs(keyword, storageCondition);
    }

    @Transactional
    public PharmacyRepository.Drug addStock(String drugId, PharmacyController.StockInRequest request, String operatorId) {
        if (request.quantity() <= 0) {
            throw new IllegalArgumentException("入库数量必须大于 0");
        }
        String reason = blank(request.reason()) ? "库存登记入库" : request.reason().trim();
        repository.addStock(drugId, request.quantity(), operatorId, reason);
        return repository.drug(drugId);
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
        String id = UUID.randomUUID().toString();
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
        if ("PATIENT".equals(role) && !patientAccessClient.owns(patientId, prescription.patientId())) {
            throw new org.springframework.security.access.AccessDeniedException("患者只能查看自己的处方");
        }
        return prescription;
    }

    public List<Prescription> list(String patientId, String status, String requesterId, String role) {
        String scopedPatientId = patientId;
        if ("PATIENT".equals(role)) {
            if (scopedPatientId == null || scopedPatientId.isBlank()) scopedPatientId = patientAccessClient.boundPatientId(requesterId);
            if (scopedPatientId == null || scopedPatientId.isBlank()) {
                throw new org.springframework.security.access.AccessDeniedException("请先添加并绑定就诊人");
            }
            if (!patientAccessClient.owns(requesterId, scopedPatientId)) {
                throw new org.springframework.security.access.AccessDeniedException("患者只能查看自己账号名下就诊人的处方");
            }
        }
        return repository.list(scopedPatientId, status);
    }

    @Transactional
    public DrugReturnOrder createDrugReturn(String prescriptionId, PharmacyController.CreateDrugReturnRequest request,
            String doctorId) {
        Prescription prescription = repository.findPrescription(prescriptionId);
        if (!canReturnBeforeDispense(prescription.status())) {
            throw new IllegalStateException("只有未缴费或已缴费未取药处方可以申请退药");
        }
        if (blank(request.doctorOpinion())) {
            throw new IllegalArgumentException("医生意见不能为空");
        }
        PrescriptionStatus targetStatus = returnStatusFor(prescription.status());
        DrugReturnStatus returnStatus = targetStatus == PrescriptionStatus.RETURN_PENDING_REFUND
                ? DrugReturnStatus.RETURN_PENDING_REFUND
                : DrugReturnStatus.RETURNED;
        DrugReturnOrder order = repository.createDrugReturn(prescription, doctorId, request.doctorOpinion(), request.opinionTemplate(), returnStatus);
        if (!repository.markReturnedBeforeDispense(prescription.id(), doctorId, "未取药退药 " + order.returnNo(), targetStatus)) {
            throw new IllegalStateException("处方状态已变化，不能创建未取药退药单");
        }
        return repository.findDrugReturn(order.id());
    }

    public List<DrugReturnOrder> drugReturns(String patientId, String status, String requesterId, String role) {
        String scopedPatientId = patientId;
        if ("PATIENT".equals(role)) {
            if (scopedPatientId == null || scopedPatientId.isBlank()) scopedPatientId = patientAccessClient.boundPatientId(requesterId);
            if (scopedPatientId == null || scopedPatientId.isBlank()) {
                throw new org.springframework.security.access.AccessDeniedException("请先添加并绑定就诊人");
            }
            if (!patientAccessClient.owns(requesterId, scopedPatientId)) {
                throw new org.springframework.security.access.AccessDeniedException("患者只能查看自己账号名下就诊人的退药单");
            }
        }
        return repository.listDrugReturns(scopedPatientId, status);
    }

    @Transactional
    public DrugReturnOrder completeDrugReturn(String id, String cashierId, String refundOrderId) {
        if (!repository.completeDrugReturn(id, cashierId, refundOrderId)) {
            DrugReturnOrder order = repository.findDrugReturn(id);
            if (order.status() == DrugReturnStatus.RETURN_REFUNDED) return order;
            throw new IllegalStateException("退药记录不在待退费状态，不能完成退费");
        }
        DrugReturnOrder order = repository.findDrugReturn(id);
        if (!repository.markReturnRefunded(order.prescriptionId())) {
            Prescription prescription = repository.findPrescription(order.prescriptionId());
            if (prescription.status() != PrescriptionStatus.RETURN_REFUNDED) {
                throw new IllegalStateException("处方不在待退费状态，不能完成退费");
            }
        }
        return repository.findDrugReturn(id);
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
        if (!canReturnBeforeDispense(prescription.status())) {
            throw new IllegalStateException("只有未缴费或已缴费未取药处方可以退药");
        }
        String returnReason = blank(reason) ? "未取药退药" : reason;
        if (!repository.markReturnedBeforeDispense(id, operatorId, returnReason, returnStatusFor(prescription.status()))) {
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
        return new PrescriptionItem(UUID.randomUUID().toString(), prescriptionId, drug.id(), drug.drugName(),
                request.quantity(), request.dosage(), request.usage(), request.frequency(), request.days(),
                request.note(), drug.unitPrice(), amount);
    }

    private String normalizeAiStatus(String value) {
        if (blank(value)) return "HUMAN_ONLY";
        if (List.of("AI_ACCEPTED", "FULL").contains(value)) return "FULL";
        if (List.of("AI_MODIFIED", "PARTIAL").contains(value)) return "PARTIAL";
        if (List.of("AI_REJECTED", "REJECTED").contains(value)) return "REJECTED";
        if ("HUMAN_ONLY".equals(value)) return "HUMAN_ONLY";
        if (!List.of("AI_ACCEPTED", "AI_MODIFIED", "AI_REJECTED", "HUMAN_ONLY").contains(value)) {
            throw new IllegalArgumentException("AI 建议处理状态不合法");
        }
        return value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
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
        throw new IllegalStateException("只有未缴费或已缴费未取药处方可以退药");
    }
}
