package com.cloudbrain.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.audit.model.AuditLogEntry;
import com.cloudbrain.audit.repository.AuditLogRepository;
import com.cloudbrain.common.audit.AuditActor;
import com.cloudbrain.common.audit.AuditEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {
    @Mock
    AuditLogRepository repository;

    @Mock
    AuditLogSearchIndexService searchIndexService;

    @Test
    void recordSavesAndIndexesEvent() {
        AuditLogService service = new AuditLogService(repository, searchIndexService);
        AuditEvent event = event("evt-1");

        service.record(event);

        verify(repository).save(event);
        verify(searchIndexService).index(event);
    }

    @Test
    void searchUsesIndexedEventIdsWhenKeywordMatches() {
        AuditLogService service = new AuditLogService(repository, searchIndexService);
        AuditLogSearchCriteria criteria = new AuditLogSearchCriteria(
                "audit-service", "LOGIN", "USER", "user-1", "user-1",
                "patient-1", "biz-1", "search", Instant.parse("2026-07-09T00:00:00Z"),
                Instant.parse("2026-07-10T00:00:00Z"), 50);
        List<AuditLogEntry> entries = List.of(entry("evt-1"));
        when(searchIndexService.searchEventIds(criteria)).thenReturn(Optional.of(List.of("evt-1")));
        when(repository.findByEventIds(List.of("evt-1"))).thenReturn(entries);

        List<AuditLogEntry> result = service.search(criteria);

        assertThat(result).isSameAs(entries);
        verify(repository).findByEventIds(List.of("evt-1"));
        verify(repository, never()).search(any());
    }

    @Test
    void searchFallsBackToRepositoryWhenIndexHasNoMatches() {
        AuditLogService service = new AuditLogService(repository, searchIndexService);
        AuditLogSearchCriteria criteria = new AuditLogSearchCriteria(
                null, null, null, null, null, null, null, "search", null, null, 20);
        List<AuditLogEntry> entries = List.of(entry("evt-2"));
        when(searchIndexService.searchEventIds(criteria)).thenReturn(Optional.of(List.of()));
        when(repository.search(criteria)).thenReturn(entries);

        List<AuditLogEntry> result = service.search(criteria);

        assertThat(result).isSameAs(entries);
        verify(repository).search(criteria);
        verify(repository, never()).findByEventIds(any());
    }

    @Test
    void reindexCountsOnlySuccessfulIndexOperations() {
        AuditLogService service = new AuditLogService(repository, searchIndexService);
        AuditLogEntry first = entry("evt-1");
        AuditLogEntry second = entry("evt-2");
        AuditLogEntry third = entry("evt-3");
        when(repository.findRecentForIndex(100)).thenReturn(List.of(first, second, third));
        when(searchIndexService.index(first)).thenReturn(true);
        when(searchIndexService.index(second)).thenReturn(false);
        when(searchIndexService.index(third)).thenReturn(true);

        int indexed = service.reindexSearchIndex(100);

        assertThat(indexed).isEqualTo(2);
    }

    private AuditEvent event(String eventId) {
        return new AuditEvent(
                eventId,
                "audit-service",
                "LOGIN",
                "USER",
                "user-1",
                "patient-1",
                "biz-1",
                new AuditActor("user-1", "Alice", "ADMIN", "10.0.0.1"),
                Instant.parse("2026-07-09T10:00:00Z"),
                Map.of("result", "SUCCESS"));
    }

    private AuditLogEntry entry(String eventId) {
        return new AuditLogEntry(
                1L,
                eventId,
                "user-1",
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
