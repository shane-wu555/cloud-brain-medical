package com.cloudbrain.medicalorder.controller;

import com.cloudbrain.medicalorder.service.WorkspaceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/medical-orders")
public class WorkspaceController {
    private final WorkspaceService service;

    public WorkspaceController(WorkspaceService service) {
        this.service = service;
    }

    /**
     * Single endpoint that returns the full workbench workspace.
     * Replaces the 5+ separate HTTP requests (getMedicalOrders, getSpecimens,
     * getLabResults, getAttachments, getReports) with one call.
     */
    @GetMapping("/workspace")
    @PreAuthorize("hasAnyRole('CHECK_DOCTOR','LAB_DOCTOR','DISPOSAL_DOCTOR')")
    public WorkspaceDto workspace(
            @RequestParam(name = "orderId", required = false) String orderId,
            JwtAuthenticationToken authentication) {
        String staffId = authentication.getToken().getSubject();
        String role = authentication.getToken().getClaimAsString("role");
        return service.workspace(staffId, role, orderId);
    }
}
