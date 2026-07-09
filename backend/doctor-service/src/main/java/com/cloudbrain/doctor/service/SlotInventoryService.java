package com.cloudbrain.doctor.service;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class SlotInventoryService {
    private static final Logger log = LoggerFactory.getLogger(SlotInventoryService.class);
    private final RestClient appointmentClient;
    private final String internalApiKey;

    public SlotInventoryService(
            @Value("${internal.api-key}") String internalApiKey,
            @Value("${services.appointment.base-url:http://localhost:8104}") String appointmentUrl) {
        this.internalApiKey = internalApiKey;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.appointmentClient = RestClient.builder().requestFactory(factory).baseUrl(appointmentUrl).build();
    }

    @Cacheable(value = "slotInventory", unless = "#result.isEmpty()")
    public List<SlotDto> fetchSlots() {
        try {
            var result = appointmentClient.get().uri("/api/internal/appointment-slots")
                    .header("X-Internal-Api-Key", internalApiKey)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<SlotDto>>() {});
            return result == null ? List.of() : result;
        } catch (RestClientException exception) {
            log.warn("Appointment slot inventory query failed: message={}", exception.getMessage());
            return List.of();
        }
    }

    public record SlotDto(
            @com.fasterxml.jackson.annotation.JsonAlias("slotId") String scheduleId,
            int capacity, int locked, int booked, int available) {
    }

    public void syncSlot(String scheduleId, int capacity) {
        try {
            appointmentClient.post().uri("/api/internal/appointment-slots")
                    .header("X-Internal-Api-Key", internalApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("scheduleId", scheduleId, "capacity", capacity))
                    .retrieve().toBodilessEntity();
            evictSlotCache();
        } catch (RestClientException exception) {
            log.warn("Appointment slot sync failed: scheduleSlotId={}, capacity={}, message={}",
                    scheduleId, capacity, exception.getMessage());
        }
    }

    public void syncSlotsBatch(List<Map<String, Object>> payload) {
        if (payload == null || payload.isEmpty()) return;
        try {
            appointmentClient.post().uri("/api/internal/appointment-slots/batch")
                    .header("X-Internal-Api-Key", internalApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve().toBodilessEntity();
            evictSlotCache();
        } catch (RestClientException exception) {
            log.warn("Appointment slot batch sync failed: slots={}, message={}",
                    payload.size(), exception.getMessage());
        }
    }

    @CacheEvict(value = "slotInventory", allEntries = true)
    public void evictSlotCache() {
        // cache eviction via annotation
    }
}
