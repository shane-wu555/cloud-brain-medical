package com.cloudbrain.patient.controller;

import com.cloudbrain.patient.repository.PatientRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/api/patients")
public class PatientController {
    private final PatientRepository repository;
    private final RestClient authClient;
    private final String internalApiKey;

    public PatientController(PatientRepository repository, @Value("${services.auth.base-url}") String authUrl,
            @Value("${internal.api-key}") String internalApiKey) {
        this.repository = repository;
        this.internalApiKey = internalApiKey;
        this.authClient = RestClient.builder().baseUrl(authUrl).build();
    }

    @GetMapping("/me")
    public PatientRepository.PatientProfile me(JwtAuthenticationToken authentication) {
        Jwt jwt = authentication.getToken();
        return repository.ensure(jwt.getSubject(), jwt.getClaimAsString("phone"), jwt.getClaimAsString("name"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CASHIER','ADMIN')")
    public java.util.List<PatientRepository.PatientProfile> search(@RequestParam(name = "phone") String phone) {
        return repository.findByPhone(phone).stream().toList();
    }

    @PostMapping("/offline")
    @PreAuthorize("hasRole('CASHIER')")
    public PatientRepository.PatientProfile createOffline(@Valid @RequestBody OfflinePatientRequest request) {
        return repository.findByPhone(request.phone())
                .orElseGet(() -> repository.createOffline(request.phone(), request.name()));
    }

    @PutMapping("/me/real-name")
    public PatientRepository.PatientProfile verify(@Valid @RequestBody RealNameRequest request,
            JwtAuthenticationToken authentication) {
        Jwt jwt = authentication.getToken();
        repository.ensure(jwt.getSubject(), jwt.getClaimAsString("phone"), jwt.getClaimAsString("name"));
        PatientRepository.PatientProfile profile = repository.verify(
                jwt.getSubject(),
                request.name().trim(),
                request.idCard() == null || request.idCard().isBlank() ? null : request.idCard().trim().toUpperCase(),
                null,
                null);
        authClient.put().uri("/api/auth/internal/users/{id}/real-name", jwt.getSubject())
                .header("X-Internal-Api-Key", internalApiKey)
                .retrieve()
                .toBodilessEntity();
        return profile;
    }

    public record RealNameRequest(@NotBlank String name, String idCard) {
    }

    public record OfflinePatientRequest(
            @NotBlank @Pattern(regexp = "^1\\d{10}$") String phone,
            @NotBlank String name) {
    }
}
