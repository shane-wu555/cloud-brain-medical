package com.cloudbrain.appointment.service;

import com.cloudbrain.appointment.entity.Appointment;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class MedicalRecordClient {
    private final RestClient restClient = RestClient.builder()
            .baseUrl("http://localhost:8105")
            .build();

    public void createInitialRecord(Appointment appointment) {
        try {
            restClient.post()
                    .uri("/api/medical-records/initial")
                    .body(Map.of(
                            "appointmentId", appointment.getId(),
                            "patientId", appointment.getPatientId(),
                            "patientName", appointment.getPatientName(),
                            "doctorId", appointment.getDoctorId(),
                            "doctorName", appointment.getDoctorName(),
                            "departmentName", appointment.getDepartmentName(),
                            "triageSummary", appointment.getTriageSummary(),
                            "riskLevel", appointment.getRiskLevel(),
                            "visitDate", appointment.getVisitDate().toString(),
                            "period", appointment.getPeriod()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ignored) {
            // The appointment flow should remain available during local development if the record service is restarting.
        }
    }
}

