package com.cloudbrain.medicalorder.service;

import com.cloudbrain.medicalorder.domain.*;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;

@Component
public class ClinicalWorkflowClient {
    private static final Logger log = LoggerFactory.getLogger(ClinicalWorkflowClient.class);
    private final RestClient appointments,records;private final String key;
    public ClinicalWorkflowClient(@Value("${services.appointment.base-url}")String appointmentUrl,@Value("${services.medical-record.base-url}")String recordUrl,@Value("${internal.api-key}")String key){appointments=RestClient.builder().baseUrl(appointmentUrl).build();records=RestClient.builder().baseUrl(recordUrl).build();this.key=key;}
    public void publish(MedicalOrder order,MedicalReport report){
        try {
            records.post().uri("/api/medical-records/internal/{id}/reports",order.appointmentId()).header("X-Internal-Api-Key",key).body(Map.of("medicalOrderId",order.id(),"reportId",report.id(),"reportType",report.reportType(),"conclusion",report.conclusion(),"confirmedBy",report.confirmedBy(),"confirmedAt",report.confirmedAt().toString())).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Failed to sync medical report {} to record for appointment {}", report.id(), order.appointmentId(), e);
        }
        try {
            appointments.post().uri("/api/internal/appointments/{id}/revisit",order.appointmentId()).header("X-Internal-Api-Key",key).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Failed to enqueue revisit for appointment {} after report {}", order.appointmentId(), report.id(), e);
        }
    }
}
