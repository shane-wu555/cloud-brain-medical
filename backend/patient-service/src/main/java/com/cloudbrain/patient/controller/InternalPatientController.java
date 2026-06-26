package com.cloudbrain.patient.controller;

import com.cloudbrain.patient.repository.PatientRepository;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/internal/patients")
public class InternalPatientController {
    private final PatientRepository repository;
    private final String internalApiKey;

    public InternalPatientController(PatientRepository repository, @Value("${internal.api-key}") String key) {
        this.repository = repository;
        this.internalApiKey = key;
    }

    @GetMapping("/{id}/verification")
    public Map<String, Boolean> verification(@PathVariable("id") String id,
            @RequestHeader(name = "X-Internal-Api-Key", required = false) String key) {
        check(key);
        return Map.of("realNameVerified", repository.find(id).isPresent());
    }

    @GetMapping("/{id}/ownership")
    public Map<String, Boolean> ownership(@PathVariable("id") String id,
            @RequestParam("accountId") String accountId,
            @RequestHeader(name = "X-Internal-Api-Key", required = false) String key) {
        check(key);
        return Map.of("owned", repository.owns(accountId, id));
    }

    @GetMapping("/accounts/{accountId}/binding")
    public Map<String, Object> binding(@PathVariable("accountId") String accountId,
            @RequestHeader(name = "X-Internal-Api-Key", required = false) String key) {
        check(key);
        PatientRepository.PatientAccountState state = repository.accountState(accountId);
        return Map.of(
                "hasBoundPatient", state.hasBoundPatient(),
                "boundPatientId", state.boundPatient() == null ? "" : state.boundPatient().id());
    }

    private void check(String key) {
        if (!internalApiKey.equals(key)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "内部接口认证失败");
        }
    }
}
