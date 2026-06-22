package com.cloudbrain.appointment.controller;

import com.cloudbrain.appointment.entity.SlotInventory;
import com.cloudbrain.appointment.service.AppointmentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/internal/appointment-slots")
public class InternalSlotController {
    private final AppointmentService service;
    private final String internalApiKey;

    public InternalSlotController(AppointmentService service, @Value("${internal.api-key}") String internalApiKey) {
        this.service = service;
        this.internalApiKey = internalApiKey;
    }

    @PostMapping
    public SlotInventory sync(
            @RequestHeader(name = "X-Internal-Api-Key", required = false) String apiKey,
            @RequestBody AppointmentController.SyncSlotRequest request) {
        if (!internalApiKey.equals(apiKey)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "内部接口认证失败");
        return service.syncSlot(request.scheduleId(), request.capacity());
    }

    @GetMapping
    public java.util.List<SlotInventory> list(
            @RequestHeader(name = "X-Internal-Api-Key", required = false) String apiKey) {
        if (!internalApiKey.equals(apiKey)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "内部接口认证失败");
        return service.slots();
    }
}
