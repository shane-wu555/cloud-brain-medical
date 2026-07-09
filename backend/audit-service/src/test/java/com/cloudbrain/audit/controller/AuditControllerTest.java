package com.cloudbrain.audit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.audit.model.AuditLogEntry;
import com.cloudbrain.audit.service.AuditLogService;
import com.cloudbrain.common.audit.AuditActor;
import com.cloudbrain.common.audit.AuditEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditControllerTest {
    @Mock
    AuditLogService service;

    @Test
    void logsBuildsCriteriaAndDelegatesToService() {
        AuditController controller = new AuditController(service);
        Instant from = Instant.parse("2026-07-09T00:00:00Z");
        Instant to = Instant.parse("2026-07-10T00:00:00Z");
        List<AuditLogEntry> entries = List.of(entry());
        when(service.search(argThat(criteria -> true))).thenReturn(entries);

        List<AuditLogEntry> result = controller.logs(
                "audit-service",
                "LOGIN",
                "USER",
                "user-1",
                "operator-1",
                "patient-1",
                "biz-1",
                "keyword",
                from,
                to,
                123);

        assertThat(result).isSameAs(entries);
        verify(service).search(argThat(criteria ->
                "audit-service".equals(criteria.service())
                        && "LOGIN".equals(criteria.action())
                        && "USER".equals(criteria.resourceType())
                        && "user-1".equals(criteria.resourceId())
                        && "operator-1".equals(criteria.userId())
                        && "patient-1".equals(criteria.patientId())
                        && "biz-1".equals(criteria.businessId())
                        && "keyword".equals(criteria.keyword())
                        && from.equals(criteria.from())
                        && to.equals(criteria.to())
                        && criteria.limit() == 123));
    }

    @Test
    void reindexSearchReturnsIndexedCount() {
        AuditController controller = new AuditController(service);
        when(service.reindexSearchIndex(88)).thenReturn(7);

        Map<String, Integer> result = controller.reindexSearch(88);

        assertThat(result).isEqualTo(Map.of("indexed", 7));
    }

    @Test
    void ingestDelegatesToService() {
        AuditController controller = new AuditController(service);
        AuditEvent event = new AuditEvent(
                "evt-1",
                "cashier-service",
                "PAYMENT_CONFIRMED",
                "PAYMENT_ORDER",
                "payment-1",
                "patient-1",
                "biz-1",
                new AuditActor("cashier-1", "Cashier", "CASHIER", "10.0.0.1"),
                Instant.parse("2026-07-09T10:00:00Z"),
                Map.of("amount", "0.01"));

        controller.ingest(event);

        verify(service).record(event);
    }

    private AuditLogEntry entry() {
        return new AuditLogEntry(
                1L,
                "evt-1",
                "operator-1",
                "Alice",
                "ADMIN",
                "audit-service",
                "USER",
                "user-1",
                "patient-1",
                "biz-1",
                "LOGIN",
                "10.0.0.1",
                Instant.parse("2026-07-09T10:00:00Z"),
                Map.of("result", "SUCCESS"));
    }
}
