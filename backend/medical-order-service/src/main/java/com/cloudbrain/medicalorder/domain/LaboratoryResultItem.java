package com.cloudbrain.medicalorder.domain;

import java.time.LocalDateTime;

public record LaboratoryResultItem(
        String id, String medicalOrderId, String specimenId, String itemCode, String itemName,
        String resultValue, String unit, String referenceRange, String abnormalFlag,
        String createdByType, String aiRecordId, String confirmedBy,
        LocalDateTime confirmedAt, LocalDateTime createdAt) {}
