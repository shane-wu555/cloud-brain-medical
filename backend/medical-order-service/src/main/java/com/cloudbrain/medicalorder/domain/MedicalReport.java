package com.cloudbrain.medicalorder.domain;
import java.time.LocalDateTime;
public record MedicalReport(String id,String medicalOrderId,String reportType,String status,String findings,String conclusion,
        String advice,String createdByType,String aiTaskId,String aiOriginalFindings,String aiOriginalConclusion,
        boolean modifiedFromAi,String confirmedBy,LocalDateTime confirmedAt,String rejectionReason,LocalDateTime updatedAt){}
