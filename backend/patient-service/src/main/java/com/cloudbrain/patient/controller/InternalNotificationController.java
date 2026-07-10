package com.cloudbrain.patient.controller;

import com.cloudbrain.patient.repository.NotificationRepository;
import com.cloudbrain.patient.repository.NotificationRepository.CreateRequest;
import com.cloudbrain.patient.repository.NotificationRepository.PatientNotification;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/internal/patients")
public class InternalNotificationController {
    private final NotificationRepository repository;
    private final String internalApiKey;

    public InternalNotificationController(
            NotificationRepository repository,
            @Value("${internal.api-key}") String key) {
        this.repository = repository;
        this.internalApiKey = key;
    }

    @PostMapping("/notifications")
    public PatientNotification create(
            @RequestBody CreateNotificationRequest request,
            @RequestHeader(name = "X-Internal-Api-Key", required = false) String key) {
        check(key);
        if (request.patientId() == null || request.patientId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientId is required");
        }
        if (request.category() == null || request.category().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "category is required");
        }
        if (request.title() == null || request.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required");
        }
        if (request.referenceType() == null || request.referenceType().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "referenceType is required");
        }
        if (request.referenceId() == null || request.referenceId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "referenceId is required");
        }
        return repository.create(new CreateRequest(
                request.patientId(), request.category(), request.title(), request.body(),
                request.referenceType(), request.referenceId()));
    }

    private void check(String key) {
        if (!internalApiKey.equals(key)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "内部接口认证失败");
        }
    }

    public record CreateNotificationRequest(
            String patientId, String category, String title, String body,
            String referenceType, String referenceId) {}
}
