package com.cloudbrain.medicalorder.domain;
import java.time.LocalDateTime;
public record AiMedicalTask(String id,String medicalOrderId,String externalTaskId,String taskType,String status,String modelVersion,String rawOutput,String errorMessage,LocalDateTime updatedAt){}
