package com.cloudbrain.audit.controller;

import com.cloudbrain.audit.model.AuditLogEntry;
import com.cloudbrain.audit.service.AuditLogSearchCriteria;
import com.cloudbrain.audit.service.AuditLogService;
import com.cloudbrain.common.audit.AuditEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuditController {
    private final AuditLogService service;

    public AuditController(AuditLogService service) {
        this.service = service;
    }

    @GetMapping("/audit/logs")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuditLogEntry> logs(
            @RequestParam(name = "service", required = false) String serviceName,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "resourceType", required = false) String resourceType,
            @RequestParam(name = "resourceId", required = false) String resourceId,
            @RequestParam(name = "userId", required = false) String userId,
            @RequestParam(name = "patientId", required = false) String patientId,
            @RequestParam(name = "businessId", required = false) String businessId,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "from", required = false) Instant from,
            @RequestParam(name = "to", required = false) Instant to,
            @RequestParam(name = "limit", defaultValue = "200") int limit) {
        return service.search(new AuditLogSearchCriteria(
                serviceName, action, resourceType, resourceId, userId, patientId, businessId, keyword, from, to, limit));
    }

    @PostMapping("/audit/logs/search-index/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Integer> reindexSearch(
            @RequestParam(name = "limit", defaultValue = "5000") int limit) {
        return Map.of("indexed", service.reindexSearchIndex(limit));
    }

    @PostMapping("/internal/audit/logs")
    public void ingest(@RequestBody AuditEvent event) {
        service.record(event);
    }
}
