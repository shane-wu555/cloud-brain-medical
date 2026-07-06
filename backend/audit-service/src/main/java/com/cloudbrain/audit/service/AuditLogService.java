package com.cloudbrain.audit.service;

import com.cloudbrain.audit.model.AuditLogEntry;
import com.cloudbrain.audit.repository.AuditLogRepository;
import com.cloudbrain.common.audit.AuditEvent;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {
    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(AuditEvent event) {
        repository.save(event);
    }

    public List<AuditLogEntry> search(AuditLogSearchCriteria criteria) {
        return repository.search(criteria);
    }
}
