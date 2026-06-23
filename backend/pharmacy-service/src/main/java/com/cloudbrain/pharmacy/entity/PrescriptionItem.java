package com.cloudbrain.pharmacy.entity;

import java.math.BigDecimal;

public record PrescriptionItem(
        String id,
        String prescriptionId,
        String drugId,
        String drugName,
        int quantity,
        String dosage,
        String usage,
        String frequency,
        int days,
        String note,
        BigDecimal unitPrice,
        BigDecimal amount) {
}
