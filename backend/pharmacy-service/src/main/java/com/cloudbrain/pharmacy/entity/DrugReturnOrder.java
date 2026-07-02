package com.cloudbrain.pharmacy.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record DrugReturnOrder(
        String id,
        String returnNo,
        String prescriptionId,
        String prescriptionNo,
        String patientId,
        String patientName,
        String doctorId,
        String doctorOpinion,
        String opinionTemplate,
        DrugReturnStatus status,
        BigDecimal totalAmount,
        String pharmacistId,
        String pharmacistOpinion,
        String cashierId,
        String refundOrderId,
        LocalDateTime createdAt,
        LocalDateTime verifiedAt,
        LocalDateTime completedAt,
        List<DrugReturnItem> items) {
}
