package com.cloudbrain.appointment.entity;

public class SlotInventory {
    private final String slotId;
    private int capacity;
    private int locked;
    private int booked;

    public SlotInventory(String slotId, int capacity, int booked) {
        this.slotId = slotId;
        this.capacity = capacity;
        this.booked = booked;
    }

    public String getSlotId() { return slotId; }
    public int getCapacity()  { return capacity; }
    public int getLocked()    { return locked; }
    public int getBooked()    { return booked; }
    public int getAvailable() { return capacity - booked - locked; }

    public void lock() {
        if (getAvailable() <= 0) throw new IllegalStateException("当前号源已约满");
        locked++;
    }

    public void confirm() {
        if (locked <= 0) throw new IllegalStateException("没有可确认的锁定号源");
        locked--;
        booked++;
    }

    public void releasePaidOrLocked(boolean wasPaid) {
        if (wasPaid && booked > 0) { booked--; return; }
        if (!wasPaid && locked > 0) locked--;
    }

    public void resize(int newCapacity) {
        if (newCapacity < locked + booked) throw new IllegalArgumentException("号源容量不能小于已锁定与已预约数量");
        this.capacity = newCapacity;
    }
}
