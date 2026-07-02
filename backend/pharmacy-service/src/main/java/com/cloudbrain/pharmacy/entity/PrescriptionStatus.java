package com.cloudbrain.pharmacy.entity;

public enum PrescriptionStatus {
    DRAFT,
    CONFIRMED,
    PENDING_PAYMENT,
    PAID,
    WAITING_DISPENSE,
    DISPENSED,
    RETURNED,
    RETURN_PENDING_REFUND,
    RETURN_REFUNDED,
    CANCELLED
}
