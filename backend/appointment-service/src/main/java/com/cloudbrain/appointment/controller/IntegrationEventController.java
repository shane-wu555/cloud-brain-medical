package com.cloudbrain.appointment.controller;

import com.cloudbrain.appointment.repository.MedicalRecordEventRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/appointments/integration-events") @PreAuthorize("hasRole('ADMIN')")
public class IntegrationEventController {
    private final MedicalRecordEventRepository repository;
    public IntegrationEventController(MedicalRecordEventRepository repository){this.repository=repository;}
    @GetMapping public List<MedicalRecordEventRepository.EventView> list(@RequestParam(name = "status", required=false) String status){return repository.findEvents(status);}
    @PostMapping("/{id}/retry") public void retry(@PathVariable("id") String id){repository.retry(id);}
}
