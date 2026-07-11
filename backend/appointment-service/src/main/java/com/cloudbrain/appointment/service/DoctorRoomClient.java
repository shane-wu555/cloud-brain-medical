package com.cloudbrain.appointment.service;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DoctorRoomClient {
    private static final Logger log = LoggerFactory.getLogger(DoctorRoomClient.class);
    private final RestClient client;
    private final String internalApiKey;

    public DoctorRoomClient(
            @Value("${services.doctor.base-url:http://localhost:8103}") String url,
            @Value("${internal.api-key}") String internalApiKey) {
        this.client = RestClient.builder().baseUrl(url).build();
        this.internalApiKey = internalApiKey;
    }

    public Optional<String> roomNameForDoctor(String doctorId) {
        if (doctorId == null || doctorId.isBlank()) {
            return Optional.empty();
        }
        try {
            DoctorRoom detail = client.get()
                    .uri("/api/internal/doctor-operations/doctors/{id}/room", doctorId)
                    .header("X-Internal-Api-Key", internalApiKey)
                    .retrieve()
                    .body(DoctorRoom.class);
            if (detail == null || detail.roomName() == null || detail.roomName().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(detail.roomName());
        } catch (Exception exception) {
            log.warn("Failed to read doctor {} room name: {}", doctorId, exception.getMessage());
            return Optional.empty();
        }
    }

    public record DoctorRoom(String doctorId, String roomId, String roomName) {}
}
