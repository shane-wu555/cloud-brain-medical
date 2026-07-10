package com.cloudbrain.medicalorder.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.cloudbrain.medicalorder.domain.LaboratoryResultItem;
import com.cloudbrain.medicalorder.domain.Specimen;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class LaboratoryWorkflowRepositoryTest {
    private final JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);

    @Test
    void repositoryCoversSpecimenLifecycleAndResults() {
        LaboratoryWorkflowRepository repository = Mockito.spy(new LaboratoryWorkflowRepository(jdbc));
        Specimen specimen = specimen("specimen-1", "order-1", "ANALYZING");
        LaboratoryResultItem item = item("result-1", "order-1", "specimen-1");
        when(jdbc.queryForObject("select count(*) from medical_order where id = ?::uuid and order_type = 'LAB'", Integer.class, "order-1"))
                .thenReturn(1);
        doReturn(Optional.of(specimen)).when(repository).specimen(any());
        doReturn(List.of(item)).when(repository).results("order-1");

        assertThat(repository.isLabOrder("order-1")).isTrue();
        assertThat(repository.createSpecimen("order-1", "BLOOD", "LAB-1")).isEqualTo(specimen);
        assertThat(repository.transition("specimen-1", "REQUESTED", "COLLECTED", "doctor-1", null)).isFalse();
        when(jdbc.update(contains("update specimen set status = ?"), eq("COLLECTED"), eq("COLLECTED"), eq(null), eq("specimen-1"), eq("REQUESTED"))).thenReturn(1);
        assertThat(repository.transition("specimen-1", "REQUESTED", "COLLECTED", "doctor-1", null)).isTrue();
        assertThat(repository.upsertResult("order-1", "specimen-1", "WBC", "WBC", "10.0", "%", "3-10", "NORMAL", "HUMAN", null, "doctor-1"))
                .isEqualTo(item);
    }

    @Test
    void selectQueriesReturnMappedSpecimensAndResults() {
        LaboratoryWorkflowRepository repository = new LaboratoryWorkflowRepository(jdbc);
        Specimen specimen = specimen("specimen-1", "order-1", "RECEIVED");
        LaboratoryResultItem item = item("result-1", "order-1", "specimen-1");
        when(jdbc.query(contains("from specimen where id"), any(RowMapper.class), eq("specimen-1"))).thenReturn(List.of(specimen));
        when(jdbc.query(contains("from specimen where order_id"), any(RowMapper.class), eq("order-1"))).thenReturn(List.of(specimen));
        when(jdbc.query(contains("from lab_result_item where order_id"), any(RowMapper.class), eq("order-1"))).thenReturn(List.of(item));

        assertThat(repository.specimen("specimen-1")).contains(specimen);
        assertThat(repository.specimens("order-1")).containsExactly(specimen);
        assertThat(repository.results("order-1")).containsExactly(item);
    }

    private Specimen specimen(String id, String orderId, String status) {
        return new Specimen(id, orderId, "BLOOD", "LAB-1", status, null, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), null, null, null, null, LocalDateTime.now());
    }

    private LaboratoryResultItem item(String id, String orderId, String specimenId) {
        return new LaboratoryResultItem(id, orderId, specimenId, "WBC", "WBC", "10.0", "%", "3-10", "NORMAL", "HUMAN", null, "doctor-1", LocalDateTime.now(), LocalDateTime.now());
    }
}
