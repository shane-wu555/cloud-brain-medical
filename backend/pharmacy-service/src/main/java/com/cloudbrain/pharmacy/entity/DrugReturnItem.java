package com.cloudbrain.pharmacy.entity;

import java.math.BigDecimal;

public record DrugReturnItem(
        String id,
        String returnId,
        String prescriptionItemId,
        String drugId,
        String drugName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal amount,
        String batchNo,
        Boolean batchNoMatched,
        Boolean coldChainOrOpenedRejectType,
        Boolean packageIntact,
        Boolean sealBroken,
        String pharmacistNote) {
}
