package com.cloudbrain.medicalorder.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MedicalOrder(
        String id, String appointmentId, String patientId, String patientName, String orderingDoctorId,
        String orderType, String projectCode, String projectName, String purpose, String bodyPart,
        BigDecimal amount, String paymentStatus, String status, String executorId,
        String executorName,String executionLocation,String equipmentId,Integer queueNumber,String urgency,
        String triageCreatedByType,String triageReasons,int missedCount,
        String resultData, String resultSummary, LocalDateTime createdAt, LocalDateTime startedAt, LocalDateTime completedAt) {}
