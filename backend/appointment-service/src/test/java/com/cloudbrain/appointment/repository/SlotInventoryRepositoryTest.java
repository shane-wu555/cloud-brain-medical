package com.cloudbrain.appointment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.appointment.entity.SlotInventory;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class SlotInventoryRepositoryTest {
    @Mock JdbcTemplate jdbcTemplate;

    @Test
    void findAllAndFindByScheduleIdMapInventoryRows() throws Exception {
        SlotInventoryRepository repository = new SlotInventoryRepository(jdbcTemplate);
        when(jdbcTemplate.query(eq("select * from slot_inventory order by slot_id"), any(RowMapper.class)))
                .thenAnswer(invocation -> List.of(mapInventory(invocation.getArgument(1), Map.of(
                        "slot_id", "slot-1",
                        "capacity", 10,
                        "locked", 2,
                        "booked", 3))));
        when(jdbcTemplate.query(eq("select * from slot_inventory where slot_id = ?"), any(RowMapper.class), eq("slot-2")))
                .thenAnswer(invocation -> List.of(mapInventory(invocation.getArgument(1), Map.of(
                        "slot_id", "slot-2",
                        "capacity", 8,
                        "locked", 1,
                        "booked", 2))));

        List<SlotInventory> all = repository.findAll();
        Optional<SlotInventory> found = repository.findByScheduleId("slot-2");

        assertThat(all.get(0).getLocked()).isEqualTo(2);
        assertThat(all.get(0).getBooked()).isEqualTo(3);
        assertThat(found).isPresent();
        assertThat(found.orElseThrow().getLocked()).isEqualTo(1);
    }

    @Test
    void saveAndSaveAllCapacitiesPersistInventory() {
        SlotInventoryRepository repository = new SlotInventoryRepository(jdbcTemplate);
        SlotInventory inventory = new SlotInventory("slot-1", 10, 3);

        assertThat(repository.save(inventory)).isSameAs(inventory);
        repository.saveAllCapacities(List.of(new SlotInventoryRepository.SlotCapacity("slot-1", 8)));
        repository.saveAllCapacities(List.of());

        verify(jdbcTemplate).update(contains("insert into slot_inventory"), eq("slot-1"), eq(10), eq(0), eq(3));
        verify(jdbcTemplate).batchUpdate(contains("insert into slot_inventory"), any(List.class), eq(500), any());
        verify(jdbcTemplate, never()).batchUpdate(anyString(), eq(List.of()), eq(500), any());
    }

    @Test
    void slotMutationMethodsReturnSuccessBasedOnUpdatedRows() {
        SlotInventoryRepository repository = new SlotInventoryRepository(jdbcTemplate);
        when(jdbcTemplate.update(contains("set locked = locked + 1"), eq("slot-1"))).thenReturn(1);
        when(jdbcTemplate.update(contains("set locked = locked - 1, booked = booked + 1"), eq("slot-1"))).thenReturn(1);
        when(jdbcTemplate.update(contains("set booked = booked + 1"), eq("slot-1"))).thenReturn(0);

        assertThat(repository.tryLock("slot-1")).isTrue();
        assertThat(repository.confirmLocked("slot-1")).isTrue();
        assertThat(repository.bookOffline("slot-1")).isFalse();

        repository.releaseLocked("slot-1");
        repository.releaseBooked("slot-1");

        verify(jdbcTemplate).update(eq("update slot_inventory set locked = locked - 1 where slot_id = ? and locked > 0"), eq("slot-1"));
        verify(jdbcTemplate).update(eq("update slot_inventory set booked = booked - 1 where slot_id = ? and booked > 0"), eq("slot-1"));
    }

    @SuppressWarnings("unchecked")
    private SlotInventory mapInventory(Object mapperObject, Map<String, Object> values) throws SQLException {
        RowMapper<SlotInventory> mapper = (RowMapper<SlotInventory>) mapperObject;
        ResultSet resultSet = org.mockito.Mockito.mock(ResultSet.class);
        when(resultSet.getString("slot_id")).thenReturn((String) values.get("slot_id"));
        when(resultSet.getInt("capacity")).thenReturn((Integer) values.get("capacity"));
        when(resultSet.getInt("locked")).thenReturn((Integer) values.get("locked"));
        when(resultSet.getInt("booked")).thenReturn((Integer) values.get("booked"));
        return mapper.mapRow(resultSet, 0);
    }
}
