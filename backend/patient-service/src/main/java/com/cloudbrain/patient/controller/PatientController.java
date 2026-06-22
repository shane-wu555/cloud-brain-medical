package com.cloudbrain.patient.controller;

import com.cloudbrain.patient.repository.PatientRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/api/patients")
public class PatientController {
    private final PatientRepository repository;
    private final RestClient authClient;
    private final String internalApiKey;
    public PatientController(PatientRepository repository, @Value("${services.auth.base-url}") String authUrl,
            @Value("${internal.api-key}") String internalApiKey) {
        this.repository = repository; this.internalApiKey = internalApiKey;
        this.authClient = RestClient.builder().baseUrl(authUrl).build();
    }

    @GetMapping("/me")
    public PatientRepository.PatientProfile me(Jwt jwt) {
        return repository.ensure(jwt.getSubject(), jwt.getClaimAsString("phone"), jwt.getClaimAsString("name"));
    }

    @PutMapping("/me/real-name")
    public PatientRepository.PatientProfile verify(@Valid @RequestBody RealNameRequest request, Jwt jwt) {
        repository.ensure(jwt.getSubject(), jwt.getClaimAsString("phone"), jwt.getClaimAsString("name"));
        if (!validIdCard(request.idCard())) throw new IllegalArgumentException("身份证号校验失败");
        LocalDate birthDate = LocalDate.of(
                Integer.parseInt(request.idCard().substring(6, 10)),
                Integer.parseInt(request.idCard().substring(10, 12)),
                Integer.parseInt(request.idCard().substring(12, 14)));
        String gender = ((request.idCard().charAt(16) - '0') % 2 == 0) ? "FEMALE" : "MALE";
        PatientRepository.PatientProfile profile = repository.verify(jwt.getSubject(), request.name(), request.idCard().toUpperCase(), gender, birthDate);
        authClient.put().uri("/api/auth/internal/users/{id}/real-name", jwt.getSubject())
                .header("X-Internal-Api-Key", internalApiKey).retrieve().toBodilessEntity();
        return profile;
    }

    static boolean validIdCard(String value) {
        if (value == null || !value.matches("^[1-9]\\d{16}[0-9Xx]$")) return false;
        int[] weights = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
        char[] checks = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
        int sum = 0;
        for (int i = 0; i < 17; i++) sum += (value.charAt(i) - '0') * weights[i];
        return Character.toUpperCase(value.charAt(17)) == checks[sum % 11];
    }

    public record RealNameRequest(@NotBlank String name,
            @NotBlank @Pattern(regexp = "^[1-9]\\d{16}[0-9Xx]$") String idCard) {}
}
