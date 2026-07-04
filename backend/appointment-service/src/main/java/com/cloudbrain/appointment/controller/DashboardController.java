package com.cloudbrain.appointment.controller;

import com.cloudbrain.appointment.repository.AppointmentRepository;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final AppointmentRepository appointmentRepository;
    private final RestClient doctorClient;
    private final String internalApiKey;

    public DashboardController(
            AppointmentRepository appointmentRepository,
            @Value("${services.doctor.base-url:http://localhost:8103}") String doctorUrl,
            @Value("${internal.api-key}") String internalApiKey) {
        this.appointmentRepository = appointmentRepository;
        this.doctorClient = RestClient.builder().baseUrl(doctorUrl).build();
        this.internalApiKey = internalApiKey;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        AppointmentRepository.DashboardAppointmentStats appointmentStats =
                appointmentRepository.dashboardStats(LocalDate.now());
        DoctorOperationsStats doctorStats = doctorOperationsStats();
        return Map.of(
                "todayAppointments", appointmentStats.todayAppointments(),
                "waitingVisits", appointmentStats.waitingVisits(),
                "activeDoctors", doctorStats.activeDoctors(),
                "scheduledRooms", doctorStats.scheduledRooms(),
                "totalRooms", doctorStats.totalRooms(),
                "roomCoverageRate", doctorStats.roomCoverageRate(),
                "aiTriageCount", appointmentStats.aiTriageCount(),
                "departmentLoads", appointmentStats.departmentLoads());
    }

    private DoctorOperationsStats doctorOperationsStats() {
        try {
            Map<String, Object> response = doctorClient.get()
                    .uri("/api/internal/doctor-operations/today")
                    .header("X-Internal-Api-Key", internalApiKey)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response == null) return DoctorOperationsStats.empty();
            return new DoctorOperationsStats(
                    intValue(response.get("activeDoctors")),
                    intValue(response.get("scheduledRooms")),
                    intValue(response.get("totalRooms")),
                    intValue(response.get("roomCoverageRate")));
        } catch (RestClientException exception) {
            return DoctorOperationsStats.empty();
        }
    }

    private int intValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value == null) return 0;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private record DoctorOperationsStats(int activeDoctors, int scheduledRooms, int totalRooms, int roomCoverageRate) {
        static DoctorOperationsStats empty() {
            return new DoctorOperationsStats(0, 0, 0, 0);
        }
    }
}
