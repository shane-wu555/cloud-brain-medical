package com.cloudbrain.patient.controller;

import com.cloudbrain.patient.repository.PatientRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
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
    public PatientRepository.PatientProfile addProfile(
            @Valid @RequestBody AddPatientRequest request,
            JwtAuthenticationToken authentication) {
        Jwt jwt = authentication.getToken();
        String idType = normalizeIdType(request.idType());
        String idNumber = normalizeIdNumber(idType, request.idNumber());
        LocalDate birthDate = request.birthDate() != null ? request.birthDate() : inferBirthDate(idType, idNumber);
        return repository.createForAccount(
                jwt.getSubject(),
                jwt.getClaimAsString("phone"),
                request.name().trim(),
                idType,
                idNumber,
                normalizeGender(request.gender()),
                birthDate);
    }

    @PutMapping("/me/bound-patient")
    public PatientRepository.PatientProfile bind(
            @Valid @RequestBody BindPatientRequest request,
            JwtAuthenticationToken authentication) {
        return repository.bind(authentication.getToken().getSubject(), request.patientId());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CASHIER','ADMIN')")
    public List<PatientRepository.PatientProfile> search(
            @RequestParam(name = "ids", required = false) String ids,
            @RequestParam(name = "phone", required = false) String phone,
            @RequestParam(name = "idNumber", required = false) String idNumber) {
        if (ids != null && !ids.isBlank()) {
            return repository.findByIds(Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .toList());
        }
        if (idNumber != null && !idNumber.isBlank()) {
            return repository.findByIdNumber("ID_CARD", idNumber);
        }
        if (phone != null && !phone.isBlank()) {
            return repository.findByPhone(phone);
        }
        throw new IllegalArgumentException("Provide idNumber or phone to search patients");
    }

    @PostMapping("/offline")
    @PreAuthorize("hasRole('CASHIER')")
    public PatientRepository.PatientProfile createOffline(@Valid @RequestBody OfflinePatientRequest request) {
        String idType = normalizeIdType(request.idType());
        String idNumber = normalizeIdNumber(idType, request.idNumber());
        String gender = request.gender() == null || request.gender().isBlank()
                ? inferGender(idType, idNumber)
                : normalizeGender(request.gender());
        LocalDate birthDate = request.birthDate() != null ? request.birthDate() : inferBirthDate(idType, idNumber);
        return repository.createOffline(idType, idNumber, request.name().trim(), request.phone(), gender, birthDate);
    }

    @PutMapping("/me/real-name")
    public PatientRepository.PatientProfile legacyVerify(
            @Valid @RequestBody LegacyRealNameRequest request,
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
                inferGender(idType, idNumber),
                inferBirthDate(idType, idNumber));
    }

    private String normalizeIdType(String idType) {
        String value = idType == null ? "" : idType.trim().toUpperCase();
        if (!ID_TYPES.contains(value)) throw new IllegalArgumentException("Unsupported certificate type");
        return value;
    }

    private String normalizeIdNumber(String idType, String idNumber) {
        String value = idNumber == null ? "" : idNumber.trim().toUpperCase();
        if (value.isBlank()) throw new IllegalArgumentException("Certificate number is required");
        if ("ID_CARD".equals(idType) && !value.matches("^\\d{17}[0-9X]$")) {
            throw new IllegalArgumentException("Invalid ID card number");
        }
        if ("ID_CARD".equals(idType)) {
            parseIdCardBirthDate(value);
        }
        if (!"ID_CARD".equals(idType) && value.length() > 64) {
            throw new IllegalArgumentException("Certificate number must be at most 64 characters");
        }
        return value;
    }

    private String normalizeGender(String gender) {
        String value = gender == null ? "" : gender.trim().toUpperCase();
        if (!GENDERS.contains(value)) throw new IllegalArgumentException("Unsupported gender");
        return value;
    }

    private LocalDate inferBirthDate(String idType, String idNumber) {
        if (!"ID_CARD".equals(idType) || idNumber.length() < 14) {
            return null;
        }
        return parseIdCardBirthDate(idNumber);
    }

    private String inferGender(String idType, String idNumber) {
        if (!"ID_CARD".equals(idType) || idNumber.length() < 17) {
            return "UNKNOWN";
        }
        return Character.digit(idNumber.charAt(16), 10) % 2 == 0 ? "FEMALE" : "MALE";
    }

    private LocalDate parseIdCardBirthDate(String idNumber) {
        try {
            return LocalDate.parse(idNumber.substring(6, 14), DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid ID card birth date");
        }
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
            @NotBlank String idType,
            @NotBlank String idNumber,
            @NotBlank String name,
            String phone,
            String gender,
            LocalDate birthDate) {
    }
}
