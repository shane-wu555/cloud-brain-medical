package com.cloudbrain.medicalorder.domain;

import java.time.LocalDateTime;

public record Specimen(
        String id, String medicalOrderId, String specimenType, String barcode, String status,
        String collectorId, LocalDateTime collectedAt, LocalDateTime receivedAt,
        LocalDateTime analyzingAt, LocalDateTime reviewedAt, LocalDateTime completedAt,
        LocalDateTime discardedAt, String discardReason, LocalDateTime createdAt) {}
