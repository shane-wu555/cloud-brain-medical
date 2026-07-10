package com.cloudbrain.medicalorder.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

class LaboratoryWorkflowRepositoryBranchTest {
    private final JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
    private final LaboratoryWorkflowRepository repository = new LaboratoryWorkflowRepository(jdbc);

    @Test
    void isLabOrderReturnsFalseForZeroOrNullCount() {
        when(jdbc.queryForObject(contains("count(*) from medical_order"), eq(Integer.class), eq("order-1"))).thenReturn(0);
        assertThat(repository.isLabOrder("order-1")).isFalse();

        when(jdbc.queryForObject(contains("count(*) from medical_order"), eq(Integer.class), eq("order-2"))).thenReturn(null);
        assertThat(repository.isLabOrder("order-2")).isFalse();
    }

    @Test
    void transitionCoversAllTimestampBranchesAndRejectsUnknownStatus() {
        when(jdbc.update(contains("collected_at"), eq("COLLECTED"), eq("COLLECTED"), eq(null), eq("specimen"), eq("REQUESTED"))).thenReturn(1);
        when(jdbc.update(contains("received_at"), eq("RECEIVED"), eq("RECEIVED"), eq(null), eq("specimen"), eq("COLLECTED"))).thenReturn(1);
        when(jdbc.update(contains("analyzed_at"), eq("ANALYZING"), eq("ANALYZING"), eq(null), eq("specimen"), eq("RECEIVED"))).thenReturn(1);
        when(jdbc.update(contains("completed_at"), eq("REVIEWED"), eq("REVIEWED"), eq(null), eq("specimen"), eq("ANALYZING"))).thenReturn(1);
        when(jdbc.update(contains("completed_at"), eq("EXHAUSTED"), eq("EXHAUSTED"), eq(null), eq("specimen"), eq("REVIEWED"))).thenReturn(1);
        when(jdbc.update(contains("discarded_at"), eq("DISCARDED"), eq("DISCARDED"), eq("bad"), eq("specimen"), eq("REVIEWED"))).thenReturn(1);

        assertThat(repository.transition("specimen", "REQUESTED", "COLLECTED", "actor", null)).isTrue();
        assertThat(repository.transition("specimen", "COLLECTED", "RECEIVED", "actor", null)).isTrue();
        assertThat(repository.transition("specimen", "RECEIVED", "ANALYZING", "actor", null)).isTrue();
        assertThat(repository.transition("specimen", "ANALYZING", "REVIEWED", "actor", null)).isTrue();
        assertThat(repository.transition("specimen", "REVIEWED", "EXHAUSTED", "actor", null)).isTrue();
        assertThat(repository.transition("specimen", "REVIEWED", "DISCARDED", "actor", "bad")).isTrue();
        assertThatThrownBy(() -> repository.transition("specimen", "REVIEWED", "OTHER", "actor", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
