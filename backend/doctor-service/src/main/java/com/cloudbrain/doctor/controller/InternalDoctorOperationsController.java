package com.cloudbrain.doctor.controller;

import com.cloudbrain.doctor.repository.DoctorCatalogRepository;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/internal/doctor-operations")
public class InternalDoctorOperationsController {
    private final DoctorCatalogRepository repository;
    private final String internalApiKey;

    public InternalDoctorOperationsController(
            DoctorCatalogRepository repository,
            @Value("${internal.api-key}") String internalApiKey) {
        this.repository = repository;
        this.internalApiKey = internalApiKey;
    }

    @GetMapping("/today")
    public DoctorCatalogRepository.DoctorOperationsStats today(
            @RequestHeader(name = "X-Internal-Api-Key", required = false) String apiKey) {
        validateInternalKey(apiKey);
        return repository.doctorOperationsStats(LocalDate.now());
    }

    @GetMapping("/doctors/{id}/room")
    public DoctorRoom doctorRoom(
            @PathVariable("id") String id,
            @RequestHeader(name = "X-Internal-Api-Key", required = false) String apiKey) {
        validateInternalKey(apiKey);
        DoctorCatalogRepository.Doctor doctor = repository.findDoctor(id);
        return new DoctorRoom(doctor.id(), doctor.roomId(), doctor.roomName());
    }

    private void validateInternalKey(String apiKey) {
        if (!internalApiKey.equals(apiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "internal api authentication failed");
        }
    }

    public record DoctorRoom(String doctorId, String roomId, String roomName) {}
}
