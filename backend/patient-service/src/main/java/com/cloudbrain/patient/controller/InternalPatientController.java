package com.cloudbrain.patient.controller;

import com.cloudbrain.patient.repository.PatientRepository;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/internal/patients")
public class InternalPatientController {
    private final PatientRepository repository; private final String internalApiKey;
    public InternalPatientController(PatientRepository repository,@Value("${internal.api-key}") String key) {
        this.repository=repository; this.internalApiKey=key;
    }
    @GetMapping("/{id}/verification")
    public Map<String,Boolean> verification(@PathVariable("id") String id,
            @RequestHeader(name="X-Internal-Api-Key",required=false) String key) {
        if(!internalApiKey.equals(key)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"内部接口认证失败");
        return Map.of("realNameVerified",repository.find(id).map(PatientRepository.PatientProfile::realNameVerified).orElse(false));
    }
}
