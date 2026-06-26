package com.cloudbrain.appointment.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.LocalTime;
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

    @Test
    void queueLifecycleRequiresCallBeforeVisitButAlsoAllowsDirectStart() {
        Appointment called = appointment(PaymentStatus.PAID, AppointmentStatus.WAITING);
        called.markCalled();
        assertEquals(AppointmentStatus.CALLED, called.getStatus());
        called.startVisit();
        assertEquals(AppointmentStatus.IN_VISIT, called.getStatus());
    }

    private Appointment appointment(PaymentStatus paymentStatus, AppointmentStatus status) {
        return new Appointment(
                "appt-test", "schedule-test-0800", "patient-test", "Patient", "doctor-test", "Doctor",
                "dept-test", "Department", LocalDate.now().plusDays(1), "上午", LocalTime.of(8, 0),
                AppointmentSource.ONLINE, status, paymentStatus, null, "LOW", null, 1);
    }
}
