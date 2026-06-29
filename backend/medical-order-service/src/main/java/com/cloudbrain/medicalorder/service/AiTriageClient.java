package com.cloudbrain.medicalorder.service;

import com.cloudbrain.medicalorder.domain.MedicalOrder;
import com.cloudbrain.medicalorder.repository.MedicalOrderRepository.RoomCandidate;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AiTriageClient {
    private final RestClient client;

    public AiTriageClient(@Value("${services.ai.base-url}") String url) {
        client = RestClient.builder().baseUrl(url).build();
    }

    @SuppressWarnings("unchecked")
    public TriageResult triage(MedicalOrder order, List<RoomCandidate> candidates) {
        List<Map<String, Object>> payload = candidates.stream().map(c -> Map.<String, Object>of(
                "doctorId", c.id(), "doctorName", c.name(),
                "specialties", Arrays.asList(c.itemNames().split(",")),
                "currentLoad", c.currentLoad(), "capacity", c.capacity(),
                "available", true, "location", c.location(),
                "equipmentIds", c.equipmentIds() == null ? List.of()
                        : Arrays.asList(c.equipmentIds().split(","))
        )).toList();

        try {
            Map<String, Object> result = client.post().uri("/api/ai/triage").body(Map.of(
                    "orderId", order.id(),
                    "projectType", order.itemName(),
                    "bodyPart", order.bodyPart() == null ? "" : order.bodyPart(),
                    "requiredSpecialty", order.itemName(),
                    "urgency", order.urgency(),
                    "candidates", payload
            )).retrieve().body(Map.class);

            String roomId = (String) result.getOrDefault("doctorId", null);
            if (roomId == null) throw new IllegalStateException("AI 分诊响应缺少 doctorId");
            String source = String.join("；", (List<String>) result.getOrDefault("reasons", List.of()));
            return new TriageResult(roomId, "AI", source);

        } catch (Exception ignored) {
            RoomCandidate selected = candidates.stream()
                    .min(Comparator.comparingInt(RoomCandidate::currentLoad))
                    .orElseThrow(() -> new IllegalStateException("没有可用执行诊室"));
            return new TriageResult(selected.id(), "RULE", "AI 不可用，按最低负载规则分配");
        }
    }

    public record TriageResult(String roomId, String source, String reasons) {}
}
