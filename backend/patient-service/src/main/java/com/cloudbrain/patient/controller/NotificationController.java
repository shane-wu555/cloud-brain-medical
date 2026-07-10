package com.cloudbrain.patient.controller;

import com.cloudbrain.patient.repository.NotificationRepository;
import com.cloudbrain.patient.repository.NotificationRepository.PatientNotification;
import com.cloudbrain.patient.repository.PatientRepository;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/patients")
public class NotificationController {
    private final NotificationRepository repository;
    private final PatientRepository patientRepository;

    public NotificationController(NotificationRepository repository, PatientRepository patientRepository) {
        this.repository = repository;
        this.patientRepository = patientRepository;
    }

    @GetMapping("/me/notifications/count")
    public Map<String, Integer> unreadCount(JwtAuthenticationToken authentication) {
        String patientId = requireBoundPatient(authentication);
        return repository.unreadCount(patientId);
    }

    @GetMapping("/me/notifications")
    public List<PatientNotification> list(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            JwtAuthenticationToken authentication) {
        String patientId = requireBoundPatient(authentication);
        return repository.list(patientId, category, limit, offset);
    }

    @PutMapping("/me/notifications/{id}/read")
    public void markRead(@PathVariable("id") String id, JwtAuthenticationToken authentication) {
        String patientId = requireBoundPatient(authentication);
        if (!repository.markRead(id, patientId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found or already read");
        }
    }

    @PutMapping("/me/notifications/read-all")
    public void markAllRead(
            @RequestParam(value = "category", required = false) String category,
            JwtAuthenticationToken authentication) {
        String patientId = requireBoundPatient(authentication);
        repository.markAllRead(patientId, category);
    }

    private String requireBoundPatient(JwtAuthenticationToken authentication) {
        PatientRepository.PatientAccountState state =
                patientRepository.accountState(authentication.getToken().getSubject());
        if (!state.hasBoundPatient()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先绑定就诊人");
        }
        return state.boundPatient().id();
    }
}
