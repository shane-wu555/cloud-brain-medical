package com.cloudbrain.medicalorder.service;

import com.cloudbrain.medicalorder.domain.*;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ClinicalWorkflowClient {
    private final RestClient appointments,records;private final String key;
    public ClinicalWorkflowClient(@Value("${services.appointment.base-url}")String appointmentUrl,@Value("${services.medical-record.base-url}")String recordUrl,@Value("${internal.api-key}")String key){appointments=RestClient.builder().baseUrl(appointmentUrl).build();records=RestClient.builder().baseUrl(recordUrl).build();this.key=key;}
    public void publish(MedicalOrder order,MedicalReport report){records.post().uri("/api/medical-records/internal/{id}/reports",order.appointmentId()).header("X-Internal-Api-Key",key).body(Map.of("medicalOrderId",order.id(),"reportId",report.id(),"reportType",report.reportType(),"conclusion",report.conclusion(),"confirmedBy",report.confirmedBy(),"confirmedAt",report.confirmedAt().toString())).retrieve().toBodilessEntity();appointments.post().uri("/api/internal/appointments/{id}/revisit",order.appointmentId()).header("X-Internal-Api-Key",key).retrieve().toBodilessEntity();}
}
