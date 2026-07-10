package com.cloudbrain.medicalorder.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class MedicalOrderRepositoryBranchTest {
    private final JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
    private final MedicalOrderRepository repository = new MedicalOrderRepository(jdbc);

    @Test
    void existsFindAndAssignCoverFalseAndEmergencyBranches() {
        when(jdbc.queryForObject(contains("count(*) from medical_order"), eq(Integer.class), eq("appt-1"), eq("ITEM"))).thenReturn(0, null);
        assertThat(repository.existsActiveOrder("appt-1", "ITEM")).isFalse();
        assertThat(repository.existsActiveOrder("appt-1", "ITEM")).isFalse();

        when(jdbc.query(contains("from medical_order mo"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        assertThat(repository.find(" ", " ", " ", " ")).isEmpty();

        doAnswer(invocation -> null).when(jdbc).query(eq("select pg_advisory_xact_lock(hashtext(?))"), any(org.springframework.jdbc.core.RowCallbackHandler.class), eq("medical-order:room-1"));
        when(jdbc.queryForObject("select urgency from medical_order where id = ?::uuid", String.class, "order-1")).thenReturn("EMERGENCY");
        when(jdbc.queryForObject(contains("select coalesce(min(queue_number), 0) - 1"), eq(Integer.class), eq("room-1"))).thenReturn(-1);
        when(jdbc.update(contains("set room_id = ?"), eq("room-1"), eq(-1), eq("AI"), eq("matched"), eq("order-1"))).thenReturn(1);

        assertThat(repository.assign("order-1", "room-1", "AI", "matched")).isTrue();
    }

    @Test
    void moveToTailThrowsWhenOrderCannotBeMoved() {
        doAnswer(invocation -> null).when(jdbc).query(eq("select pg_advisory_xact_lock(hashtext(?))"), any(org.springframework.jdbc.core.RowCallbackHandler.class), eq("medical-order:room-1"));
        when(jdbc.queryForObject(contains("select coalesce(max(queue_number), 0) + 1"), eq(Integer.class), eq("room-1"))).thenReturn(5);
        when(jdbc.update(contains("missed_count = missed_count + 1"), eq(5), eq("order-1"), eq("room-1"))).thenReturn(0);

        assertThatThrownBy(() -> repository.moveToTail("order-1", "room-1"))
                .isInstanceOf(IllegalStateException.class);
    }
}
