package com.cloudbrain.medicalorder.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AiCtClient {
    private static final Logger log = LoggerFactory.getLogger(AiCtClient.class);

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public AiCtClient(@Value("${services.ai.base-url}") String url, ObjectMapper objectMapper) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.objectMapper = objectMapper;
        this.baseUrl = stripTrailingSlash(url);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> submit(String orderId, String objectKey, String bodyPart, String clinicalContext) {
        Map<String, Object> payload = Map.of(
                "orderId", orderId,
                "objectKey", objectKey,
                "modality", "CT",
                "bodyPart", blank(bodyPart) ? "HEAD" : bodyPart,
                "clinicalContext", blank(clinicalContext) ? "" : clinicalContext);
        String body = json(payload);
        log.info(
                "Submitting CT analysis task to AI service: orderId={}, objectKey={}, bodyBytes={}",
                orderId,
                objectKey,
                body.getBytes(StandardCharsets.UTF_8).length);
        return request("POST", "/api/ai/ct-analysis", body);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> task(String taskId) {
        String encodedTaskId = URLEncoder.encode(taskId, StandardCharsets.UTF_8);
        return request("GET", "/api/ai/tasks/" + encodedTaskId, null);
    }

    private Map<String, Object> request(String method, String path, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofMinutes(6))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Accept", "application/json")
                .header("X-CloudBrain-Caller", "medical-order-service");
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            builder.header("Content-Type", "application/json; charset=utf-8")
                    .header("X-CloudBrain-Request-Body-Bytes", String.valueOf(bodyBytes.length))
                    .method(method, HttpRequest.BodyPublishers.ofByteArray(bodyBytes));
        }
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("AI service returned HTTP " + response.statusCode() + ": " + response.body());
            }
            return objectMapper.readValue(response.body(), Map.class);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to call AI service", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI service request was interrupted", error);
        }
    }

    private String json(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Failed to serialize CT analysis request", error);
        }
    }

    private String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
