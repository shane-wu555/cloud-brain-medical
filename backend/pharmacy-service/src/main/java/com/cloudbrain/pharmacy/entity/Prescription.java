package com.cloudbrain.pharmacy.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record Prescription(
        String id,
        String prescriptionNo,
        String appointmentId,
        String medicalRecordId,
        String patientId,
        String patientName,
        String doctorId,
        String diagnosis,
        PrescriptionStatus status,
        BigDecimal totalAmount,
        String paymentOrderId,
        String aiAssistanceId,
        String aiAdoptionStatus,
        String aiRevisionNote,
        LocalDateTime createdAt,
        LocalDateTime confirmedAt,
        LocalDateTime paidAt,
        LocalDateTime dispensedAt,
        LocalDateTime returnedAt,
        String dispensedBy,
        String returnedBy,
        String returnReason,
        List<PrescriptionItem> items) {
}
