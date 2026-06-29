package com.cloudbrain.appointment.repository;

import com.cloudbrain.appointment.entity.SlotInventory;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class SlotInventoryRepository {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<SlotInventory> rowMapper = new SlotInventoryRowMapper();

    public SlotInventoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SlotInventory> findAll() {
        return jdbcTemplate.query("select * from slot_inventory order by slot_id", rowMapper);
    }

    public Optional<SlotInventory> findByScheduleId(String slotId) {
        List<SlotInventory> result = jdbcTemplate.query(
                "select * from slot_inventory where slot_id = ?",
                rowMapper,
                slotId);
        return result.stream().findFirst();
    }

    public SlotInventory save(SlotInventory inventory) {
        jdbcTemplate.update("""
                insert into slot_inventory (slot_id, capacity, locked, booked)
                values (?, ?, ?, ?)
                on conflict (slot_id) do update set
                    capacity = excluded.capacity,
                    locked   = excluded.locked,
                    booked   = excluded.booked
                """,
                inventory.getScheduleId(),
                inventory.getCapacity(),
                inventory.getLocked(),
                inventory.getBooked());
        return inventory;
    }

    public boolean tryLock(String slotId) {
        return jdbcTemplate.update("""
                update slot_inventory
                set locked = locked + 1
                where slot_id = ? and locked + booked < capacity
                """, slotId) == 1;
    }

    public boolean confirmLocked(String slotId) {
        return jdbcTemplate.update("""
                update slot_inventory
                set locked = locked - 1, booked = booked + 1
                where slot_id = ? and locked > 0
                """, slotId) == 1;
    }

    public boolean bookOffline(String slotId) {
        return jdbcTemplate.update("""
                update slot_inventory
                set booked = booked + 1
                where slot_id = ? and locked + booked < capacity
                """, slotId) == 1;
    }

    public void releaseLocked(String slotId) {
        jdbcTemplate.update("update slot_inventory set locked = locked - 1 where slot_id = ? and locked > 0", slotId);
    }

    public void releaseBooked(String slotId) {
        jdbcTemplate.update("update slot_inventory set booked = booked - 1 where slot_id = ? and booked > 0", slotId);
    }

    private static class SlotInventoryRowMapper implements RowMapper<SlotInventory> {
        @Override
        public SlotInventory mapRow(ResultSet rs, int rowNum) throws SQLException {
            SlotInventory inventory = new SlotInventory(
                    rs.getString("slot_id"),
                    rs.getInt("capacity"),
                    rs.getInt("booked"));
            for (int i = 0; i < rs.getInt("locked"); i++) {
                inventory.lock();
            }
            return inventory;
        }
    }
}
