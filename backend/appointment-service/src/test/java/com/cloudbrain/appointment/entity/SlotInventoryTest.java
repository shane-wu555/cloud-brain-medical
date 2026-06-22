package com.cloudbrain.appointment.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SlotInventoryTest {
    @Test
    void neverAllowsCapacityToBeExceeded() {
        SlotInventory inventory = new SlotInventory("schedule", 2, 1);
        inventory.lock();
        assertEquals(0, inventory.getAvailable());
        assertThrows(IllegalStateException.class, inventory::lock);
    }
}
