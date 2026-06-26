package com.cloudbrain.patient.controller;

import com.cloudbrain.patient.repository.PatientRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
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

@RestController
@RequestMapping("/api/patients")
public class PatientController {
    private static final Set<String> ID_TYPES = Set.of("ID_CARD", "PASSPORT", "HK_MACAO_TAIWAN", "OTHER");
    private static final Set<String> GENDERS = Set.of("MALE", "FEMALE", "UNKNOWN");
    private final PatientRepository repository;

    public PatientController(PatientRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/me")
    public PatientRepository.PatientAccountState me(JwtAuthenticationToken authentication) {
        return repository.accountState(authentication.getToken().getSubject());
    }

    @PostMapping("/me/profiles")
    public PatientRepository.PatientProfile addProfile(@Valid @RequestBody AddPatientRequest request,
            JwtAuthenticationToken authentication) {
        Jwt jwt = authentication.getToken();
        String idType = normalizeIdType(request.idType());
        String idNumber = normalizeIdNumber(idType, request.idNumber());
        return repository.createForAccount(
                jwt.getSubject(),
                jwt.getClaimAsString("phone"),
                request.name().trim(),
                idType,
                idNumber,
                normalizeGender(request.gender()),
                request.birthDate());
    }

    @PutMapping("/me/bound-patient")
    public PatientRepository.PatientProfile bind(@Valid @RequestBody BindPatientRequest request,
            JwtAuthenticationToken authentication) {
        return repository.bind(authentication.getToken().getSubject(), request.patientId());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CASHIER','ADMIN')")
    public List<PatientRepository.PatientProfile> search(@RequestParam(name = "phone") String phone) {
        return repository.findByPhone(phone);
    }

    @PostMapping("/offline")
    @PreAuthorize("hasRole('CASHIER')")
    public PatientRepository.PatientProfile createOffline(@Valid @RequestBody OfflinePatientRequest request) {
        return repository.findByPhone(request.phone()).stream().findFirst()
                .orElseGet(() -> repository.createOffline(request.phone(), request.name()));
    }

    @PutMapping("/me/real-name")
    public PatientRepository.PatientProfile legacyVerify(@Valid @RequestBody LegacyRealNameRequest request,
            JwtAuthenticationToken authentication) {
        Jwt jwt = authentication.getToken();
        String idType = request.idCard() == null || request.idCard().isBlank() ? "OTHER" : "ID_CARD";
        String idNumber = request.idCard() == null || request.idCard().isBlank()
                ? "UNKNOWN-" + System.currentTimeMillis()
                : normalizeIdNumber(idType, request.idCard());
        return repository.createForAccount(
                jwt.getSubject(),
                jwt.getClaimAsString("phone"),
                request.name().trim(),
                idType,
                idNumber,
                "UNKNOWN",
                null);
    }

    private String normalizeIdType(String idType) {
        String value = idType == null ? "" : idType.trim().toUpperCase();
        if (!ID_TYPES.contains(value)) throw new IllegalArgumentException("不支持的证件类型");
        return value;
    }

    private String normalizeIdNumber(String idType, String idNumber) {
        String value = idNumber == null ? "" : idNumber.trim().toUpperCase();
        if (value.isBlank()) throw new IllegalArgumentException("证件号码不能为空");
        if ("ID_CARD".equals(idType) && !value.matches("^\\d{17}[0-9X]$")) {
            throw new IllegalArgumentException("身份证号格式不正确");
        }
        if (!"ID_CARD".equals(idType) && value.length() > 64) {
            throw new IllegalArgumentException("证件号码最多 64 位");
        }
        return value;
    }

    private String normalizeGender(String gender) {
        String value = gender == null ? "" : gender.trim().toUpperCase();
        if (!GENDERS.contains(value)) throw new IllegalArgumentException("不支持的性别");
        return value;
    }

    public record AddPatientRequest(
            @NotBlank String name,
            @NotBlank String idType,
            @NotBlank String idNumber,
            @NotBlank String gender,
            LocalDate birthDate) {
    }

    public record BindPatientRequest(@NotBlank String patientId) {
    }

    public record LegacyRealNameRequest(@NotBlank String name, String idCard) {
    }

    public record OfflinePatientRequest(
            @NotBlank @Pattern(regexp = "^1\\d{10}$") String phone,
            @NotBlank String name) {
    }
}
