package com.cloudbrain.appointment.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AppointmentTest {
    @Test
    void unpaidCancellationDoesNotPretendToBeRefunded() {
        Appointment appointment = appointment(PaymentStatus.UNPAID, AppointmentStatus.PENDING_PAYMENT);
        appointment.markCancelled(false);
        assertEquals(AppointmentStatus.CANCELLED, appointment.getStatus());
        assertEquals(PaymentStatus.CANCELLED, appointment.getPaymentStatus());
    }

    @Test
    void paidCancellationIsMarkedRefunded() {
        Appointment appointment = appointment(PaymentStatus.PAID, AppointmentStatus.WAITING);
        appointment.markCancelled(true);
        assertEquals(PaymentStatus.REFUNDED, appointment.getPaymentStatus());
    }

    private Appointment appointment(PaymentStatus paymentStatus, AppointmentStatus status) {
        return new Appointment(
                "appt-test", "schedule-test", "patient-test", "患者", "doctor-test", "医生",
                "dept-test", "科室", LocalDate.now().plusDays(1), "上午", AppointmentSource.ONLINE,
                status, paymentStatus, null, "LOW", null, 1);
    }
}
