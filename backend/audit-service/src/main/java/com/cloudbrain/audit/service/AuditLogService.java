package com.cloudbrain.audit.service;

import com.cloudbrain.audit.model.AuditLogEntry;
import com.cloudbrain.audit.repository.AuditLogRepository;
import com.cloudbrain.common.audit.AuditEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {
    private final AuditLogRepository repository;
    private final AuditLogSearchIndexService searchIndexService;

    public AuditLogService(AuditLogRepository repository, AuditLogSearchIndexService searchIndexService) {
        this.repository = repository;
        this.searchIndexService = searchIndexService;
    }

    public void record(AuditEvent event) {
        repository.save(event);
        searchIndexService.index(event);
    }

    public List<AuditLogEntry> search(AuditLogSearchCriteria criteria) {
        if (!blank(criteria.keyword())) {
            Optional<List<String>> eventIds = searchIndexService.searchEventIds(criteria);
            if (eventIds.isPresent() && !eventIds.get().isEmpty()) {
                return repository.findByEventIds(eventIds.get());
            }
        }
        return repository.search(criteria);
    }

    public int reindexSearchIndex(int limit) {
        List<AuditLogEntry> entries = repository.findRecentForIndex(limit);
        int indexed = 0;
        for (AuditLogEntry entry : entries) {
            if (searchIndexService.index(entry)) {
                indexed += 1;
            }
        }
        return indexed;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
