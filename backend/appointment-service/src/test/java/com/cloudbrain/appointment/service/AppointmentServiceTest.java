package com.cloudbrain.appointment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.appointment.controller.AppointmentController;
import com.cloudbrain.appointment.entity.Appointment;
import com.cloudbrain.appointment.entity.AppointmentSource;
import com.cloudbrain.appointment.entity.AppointmentStatus;
import com.cloudbrain.appointment.entity.PaymentStatus;
import com.cloudbrain.appointment.repository.AppointmentRepository;
import com.cloudbrain.appointment.repository.MedicalRecordEventRepository;
import com.cloudbrain.appointment.repository.SlotInventoryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {
    @Mock AppointmentRepository appointments;
    @Mock SlotInventoryRepository slots;
    @Mock MedicalRecordEventRepository events;
    @Mock MedicalRecordClient medicalRecords;
    AppointmentService service;

    @BeforeEach void setUp() {
        service = new AppointmentService(appointments, slots, events, medicalRecords);
    }

    @Test void repeatedPaymentConfirmationDoesNotConsumeAnotherSlot() {
        Appointment paid = appointment(AppointmentStatus.WAITING, PaymentStatus.PAID);
        when(appointments.findByIdForUpdate("appt")).thenReturn(Optional.of(paid));
        assertThat(service.pay("appt", "WECHAT_TEST", new BigDecimal("0.01"), "patient")).isSameAs(paid);
        verify(slots, never()).confirmLocked(any());
        verify(events, never()).enqueuePayment(any(), any(), any());
    }

    @Test void expiredPendingAppointmentReleasesItsLock() {
        Appointment pending = appointment(AppointmentStatus.PENDING_PAYMENT, PaymentStatus.UNPAID);
        when(appointments.findByIdForUpdate("appt")).thenReturn(Optional.of(pending));
        service.expireOne("appt");
        verify(slots).releaseLocked("schedule-slot-0800");
        assertThat(pending.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(pending.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test void onlineAndWindowBothRejectExhaustedInventory() {
        when(slots.tryLock("schedule-slot-0800")).thenReturn(false);
        assertThatThrownBy(() -> service.lockOnline(request())).isInstanceOf(IllegalStateException.class);
        when(slots.bookOffline("schedule-slot-0800")).thenReturn(false);
        assertThatThrownBy(() -> service.createOffline(request())).isInstanceOf(IllegalStateException.class);
    }

    @Test void samePatientCannotBookSameStartTimeTwice() {
        AppointmentController.CreateAppointmentRequest request = request();
        when(appointments.existsActiveAtStartTime("patient", request.visitDate(), LocalTime.of(8, 0))).thenReturn(true);
        assertThatThrownBy(() -> service.lockOnline(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("同一就诊人同一时段不能重复预约");
        verify(slots, never()).tryLock(any());
    }

    @Test void repeatedCancellationDoesNotReleaseInventoryTwice() {
        Appointment cancelled = appointment(AppointmentStatus.CANCELLED, PaymentStatus.REFUNDED);
        when(appointments.findByIdForUpdate("appt")).thenReturn(Optional.of(cancelled));
        assertThat(service.cancel("appt", false)).isSameAs(cancelled);
        verify(slots, never()).releaseBooked(any());
        verify(slots, never()).releaseLocked(any());
    }

    @Test void visitCannotFinishBeforeMedicalRecordIsSaved() {
        Appointment visiting = appointment(AppointmentStatus.IN_VISIT, PaymentStatus.PAID);
        when(appointments.findById("appt")).thenReturn(Optional.of(visiting));
        when(medicalRecords.isSaved("appt")).thenReturn(false);
        assertThatThrownBy(() -> service.updateStatus("appt", "FINISHED", "doctor"))
                .isInstanceOf(IllegalStateException.class);
    }

    private Appointment appointment(AppointmentStatus status, PaymentStatus paymentStatus) {
        return new Appointment("appt", "schedule-slot-0800", "patient", "Patient",
                "doctor", "Doctor", "dept", "Department", LocalDate.now().plusDays(1),
                "上午", LocalTime.of(8, 0), AppointmentSource.ONLINE, status, paymentStatus,
                null, "LOW", null, 1);
    }

    private AppointmentController.CreateAppointmentRequest request() {
        return new AppointmentController.CreateAppointmentRequest("schedule-slot-0800", "patient", "Patient",
                "doctor", "Doctor", "dept", "Department", LocalDate.now().plusDays(1).toString(),
                "上午", "08:00", null, "LOW", null);
    }
}
