package com.cloudbrain.medicalorder.domain;
import java.time.LocalDateTime;
public record MedicalAttachment(String id,String medicalOrderId,String objectKey,String originalName,String contentType,long sizeBytes,String storageBucket,String uploadedBy,LocalDateTime createdAt){}
