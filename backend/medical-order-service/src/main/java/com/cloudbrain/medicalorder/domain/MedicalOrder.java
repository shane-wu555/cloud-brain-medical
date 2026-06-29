package com.cloudbrain.medicalorder.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MedicalOrder(
        String id, String appointmentId, String patientId, String patientName,
        String orderingDoctorId,
        String orderType, String itemCode, String itemName, String purpose, String bodyPart,
        BigDecimal amount, String paymentStatus, String status,
        String roomId, String roomName, String roomLocation,
        String executingStaffId,
        Integer queueNumber, String urgency,
        String triageSource, String triageReasons, int missedCount,
        String resultSummary, String resultCreatedByType, String resultAiRecordId,
        String resultConfirmedBy, LocalDateTime resultConfirmedAt,
        LocalDateTime createdAt, LocalDateTime startedAt, LocalDateTime completedAt) {}
