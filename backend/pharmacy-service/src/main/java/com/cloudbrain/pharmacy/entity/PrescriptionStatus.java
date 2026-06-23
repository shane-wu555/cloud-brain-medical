package com.cloudbrain.pharmacy.entity;

public enum PrescriptionStatus {
    DRAFT,
    CONFIRMED,
    PENDING_PAYMENT,
    PAID,
    WAITING_DISPENSE,
    DISPENSED,
    RETURNED,
    CANCELLED
}
