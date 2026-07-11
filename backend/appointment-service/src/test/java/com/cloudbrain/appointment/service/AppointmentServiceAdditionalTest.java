package com.cloudbrain.appointment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.appointment.controller.AppointmentController;
import com.cloudbrain.appointment.entity.Appointment;
import com.cloudbrain.appointment.entity.AppointmentSource;
import com.cloudbrain.appointment.entity.AppointmentStatus;
import com.cloudbrain.appointment.entity.PaymentStatus;
import com.cloudbrain.appointment.entity.SlotInventory;
import com.cloudbrain.appointment.repository.AppointmentRepository;
import com.cloudbrain.appointment.repository.MedicalRecordEventRepository;
import com.cloudbrain.appointment.repository.SlotInventoryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceAdditionalTest {
    @Mock AppointmentRepository appointments;
    @Mock SlotInventoryRepository slots;
    @Mock MedicalRecordEventRepository events;
    @Mock MedicalRecordClient medicalRecords;
    @Mock NotificationClient notifications;
    @Mock DoctorRoomClient doctorRooms;
    AppointmentService service;

    @BeforeEach
    void setUp() {
        service = new AppointmentService(appointments, slots, events, medicalRecords, notifications, doctorRooms);
    }

    @Test
    void listFiltersByDoctorPatientAndStatus() {
        Appointment match = appointment("match", "doctor-1", "patient-1", AppointmentStatus.WAITING, PaymentStatus.PAID);
        Appointment otherDoctor = appointment("other-doctor", "doctor-2", "patient-1", AppointmentStatus.WAITING, PaymentStatus.PAID);
        Appointment otherPatient = appointment("other-patient", "doctor-1", "patient-2", AppointmentStatus.WAITING, PaymentStatus.PAID);
        Appointment otherStatus = appointment("other-status", "doctor-1", "patient-1", AppointmentStatus.CANCELLED, PaymentStatus.CANCELLED);
        when(appointments.findAll()).thenReturn(List.of(match, otherDoctor, otherPatient, otherStatus));

        assertThat(service.list("doctor-1", "patient-1", "WAITING")).containsExactly(match);
    }

    @Test
    void listIncludesScheduleRoomName() {
        Appointment appointment = appointment("appt", "doctor-1", "patient-1", AppointmentStatus.WAITING, PaymentStatus.PAID);
        when(appointments.findAll()).thenReturn(List.of(appointment));
        when(doctorRooms.roomNameForDoctor("doctor-1")).thenReturn(Optional.of("全科医学2号诊室"));

        assertThat(service.list(null, "patient-1", null).get(0).getRoomName())
                .isEqualTo("全科医学2号诊室");
    }

    @Test
    void todayQueueFiltersActiveStatusesAndSortsByAppointmentTime() {
        Appointment later = appointment("later", "doctor-1", "patient-1", AppointmentStatus.WAITING, PaymentStatus.PAID, LocalDate.now(), 5);
        Appointment todayCalled = appointment("called", "doctor-1", "patient-2", AppointmentStatus.CALLED, PaymentStatus.PAID, LocalDate.now(), 2);
        Appointment finished = appointment("finished", "doctor-1", "patient-3", AppointmentStatus.FINISHED, PaymentStatus.PAID, LocalDate.now(), 3);
        Appointment otherDoctor = appointment("other", "doctor-2", "patient-4", AppointmentStatus.WAITING, PaymentStatus.PAID, LocalDate.now(), 1);
        Appointment yesterday = appointment("yesterday", "doctor-1", "patient-5", AppointmentStatus.WAITING, PaymentStatus.PAID, LocalDate.now().minusDays(1), 1);
        Appointment cancelled = appointment("cancelled", "doctor-1", "patient-6", AppointmentStatus.CANCELLED, PaymentStatus.CANCELLED, LocalDate.now(), 4);
        when(appointments.findAll()).thenReturn(List.of(later, todayCalled, finished, otherDoctor, yesterday, cancelled));

        assertThat(service.todayQueue("doctor-1")).containsExactly(todayCalled, finished, later);
    }

    @Test
    void validatePatientAccessRejectsDifferentPatientForPatientRole() {
        when(appointments.findById("appt")).thenReturn(Optional.of(appointment()));

        assertThatThrownBy(() -> service.validatePatientAccess("appt", "another-patient", "PATIENT"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void validatePatientAccessAllowsNonPatientRoles() {
        service.validatePatientAccess("appt", "another-patient", "CASHIER");
        verify(appointments, never()).findById(anyString());
    }

    @Test
    void lockOnlineCreatesPendingPaymentAppointmentWithDefaultAfternoonStartTime() {
        AppointmentController.CreateAppointmentRequest request = new AppointmentController.CreateAppointmentRequest(
                "slot-1", "patient-1", "Patient", "doctor-1", "Doctor", "dept-1", "Dept",
                LocalDate.now().plusDays(1).toString(), "AFTERNOON", "", "triage", null, "recommended", null);
        when(appointments.existsActiveAtStartTime("patient-1", request.visitDate(), LocalTime.of(14, 0))).thenReturn(false);
        when(slots.tryLock("slot-1")).thenReturn(true);
        when(appointments.nextQueueNumber("doctor-1", request.visitDate())).thenReturn(7);
        when(appointments.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Appointment created = service.lockOnline(request);

        assertThat(created.getSource()).isEqualTo(AppointmentSource.ONLINE);
        assertThat(created.getStatus()).isEqualTo(AppointmentStatus.PENDING_PAYMENT);
        assertThat(created.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(created.getStartTime()).isEqualTo(LocalTime.of(14, 0));
        assertThat(created.getRiskLevel()).isEqualTo("LOW");
        assertThat(created.getQueueNumber()).isEqualTo(7);
    }

    @Test
    void lockOnlineRejectsBlankRequiredField() {
        AppointmentController.CreateAppointmentRequest request = new AppointmentController.CreateAppointmentRequest(
                "", "patient-1", "Patient", "doctor-1", "Doctor", "dept-1", "Dept",
                LocalDate.now().plusDays(1).toString(), "MORNING", "08:00", null, "LOW", null, null);

        assertThatThrownBy(() -> service.lockOnline(request)).isInstanceOf(IllegalArgumentException.class);
        verify(slots, never()).tryLock(anyString());
    }

    @Test
    void lockOnlineRejectsVisitDateOutsideBookingWindow() {
        AppointmentController.CreateAppointmentRequest request = request(LocalDate.now().plusDays(7), "08:00");

        assertThatThrownBy(() -> service.lockOnline(request)).isInstanceOf(IllegalArgumentException.class);
        verify(slots, never()).tryLock(anyString());
    }

    @Test
    void lockOnlineTranslatesDuplicateKeyToBusinessError() {
        AppointmentController.CreateAppointmentRequest request = request(LocalDate.now().plusDays(1), "08:00");
        when(appointments.existsActiveAtStartTime("patient-1", request.visitDate(), LocalTime.of(8, 0))).thenReturn(false);
        when(slots.tryLock("slot-1")).thenReturn(true);
        when(appointments.nextQueueNumber("doctor-1", request.visitDate())).thenReturn(1);
        when(appointments.save(any(Appointment.class))).thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() -> service.lockOnline(request)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void payRejectsNonPendingAppointment() {
        Appointment waiting = appointment("appt", "doctor-1", "patient-1", AppointmentStatus.WAITING, PaymentStatus.UNPAID);
        when(appointments.findByIdForUpdate("appt")).thenReturn(Optional.of(waiting));

        assertThatThrownBy(() -> service.pay("appt", "ALI", new BigDecimal("9.90"), "cashier"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void payRejectsWhenLockedSlotCannotBeConfirmed() {
        Appointment pending = appointment("appt", "doctor-1", "patient-1", AppointmentStatus.PENDING_PAYMENT, PaymentStatus.UNPAID);
        when(appointments.findByIdForUpdate("appt")).thenReturn(Optional.of(pending));
        when(slots.confirmLocked("slot-1")).thenReturn(false);

        assertThatThrownBy(() -> service.pay("appt", "ALI", new BigDecimal("9.90"), "cashier"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void payMarksAppointmentPaidAndEnqueuesEvents() {
        Appointment pending = appointment("appt", "doctor-1", "patient-1", AppointmentStatus.PENDING_PAYMENT, PaymentStatus.UNPAID);
        when(appointments.findByIdForUpdate("appt")).thenReturn(Optional.of(pending));
        when(slots.confirmLocked("slot-1")).thenReturn(true);
        when(appointments.save(pending)).thenReturn(pending);

        Appointment paid = service.pay("appt", null, null, null);

        assertThat(paid.getStatus()).isEqualTo(AppointmentStatus.WAITING);
        assertThat(paid.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paid.getPaymentMethod()).isEqualTo("WECHAT");
        verify(events).enqueuePayment(pending, BigDecimal.ZERO, "patient-1");
        verify(events).enqueueMedicalRecord(pending);
    }

    @Test
    void cancelRejectsFinishedAppointment() {
        Appointment finished = appointment("appt", "doctor-1", "patient-1", AppointmentStatus.FINISHED, PaymentStatus.PAID);
        when(appointments.findByIdForUpdate("appt")).thenReturn(Optional.of(finished));

        assertThatThrownBy(() -> service.cancel("appt", false)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancelRejectsSameDayPatientCancellation() {
        Appointment appointment = appointment("appt", "doctor-1", "patient-1", AppointmentStatus.WAITING, PaymentStatus.PAID, LocalDate.now(), 1);
        when(appointments.findByIdForUpdate("appt")).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> service.cancel("appt", false)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancelPaidAppointmentReleasesBookedSlotAndRefunds() {
        Appointment appointment = appointment();
        appointment.markPaid("WECHAT");
        when(appointments.findByIdForUpdate("appt")).thenReturn(Optional.of(appointment));
        when(appointments.save(appointment)).thenReturn(appointment);

        Appointment cancelled = service.cancel("appt", true);

        assertThat(cancelled.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(cancelled.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(slots).releaseBooked("slot-1");
        verify(events).enqueueRefund(appointment, BigDecimal.ZERO, "patient-1");
    }

    @Test
    void cancelUnpaidAppointmentReleasesLockedSlot() {
        Appointment appointment = appointment("appt", "doctor-1", "patient-1", AppointmentStatus.PENDING_PAYMENT, PaymentStatus.UNPAID);
        when(appointments.findByIdForUpdate("appt")).thenReturn(Optional.of(appointment));
        when(appointments.save(appointment)).thenReturn(appointment);

        Appointment cancelled = service.cancel("appt", true);

        assertThat(cancelled.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(slots).releaseLocked("slot-1");
        verify(events, never()).enqueueRefund(any(), any(), anyString());
    }

    @Test
    void updateStatusFinishesAppointmentWhenMedicalRecordSaved() {
        Appointment visit = appointment("appt", "doctor-1", "patient-1", AppointmentStatus.IN_VISIT, PaymentStatus.PAID);
        when(appointments.findById("appt")).thenReturn(Optional.of(visit));
        when(medicalRecords.isSaved("appt")).thenReturn(true);
        when(appointments.save(visit)).thenReturn(visit);

        Appointment finished = service.updateStatus("appt", "FINISHED", "doctor-1");

        assertThat(finished.getStatus()).isEqualTo(AppointmentStatus.FINISHED);
    }

    @Test
    void updateStatusRejectsUnknownStatus() {
        Appointment visit = appointment("appt", "doctor-1", "patient-1", AppointmentStatus.IN_VISIT, PaymentStatus.PAID);
        when(appointments.findById("appt")).thenReturn(Optional.of(visit));

        assertThatThrownBy(() -> service.updateStatus("appt", "WAITING", "doctor-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void callAndStartVisitDelegateAfterDoctorValidation() {
        Appointment waiting = appointment("appt", "doctor-1", "patient-1", AppointmentStatus.WAITING, PaymentStatus.PAID);
        when(appointments.findByIdForUpdate("appt")).thenReturn(Optional.of(waiting));
        when(appointments.save(waiting)).thenReturn(waiting);
        when(doctorRooms.roomNameForDoctor("doctor-1")).thenReturn(Optional.of("全科医学2号诊室"));

        Appointment called = service.call("appt", "doctor-1");
        assertThat(called.getStatus()).isEqualTo(AppointmentStatus.CALLED);
        verify(notifications).notify(
                eq("patient-1"),
                eq("CALLED"),
                eq("您挂号的Doctor已叫号，请前往全科医学2号诊室就诊"),
                eq(null),
                eq("APPOINTMENT"),
                eq("appt"));

        waiting = appointment("appt2", "doctor-1", "patient-1", AppointmentStatus.CALLED, PaymentStatus.PAID);
        when(appointments.findByIdForUpdate("appt2")).thenReturn(Optional.of(waiting));
        when(appointments.save(waiting)).thenReturn(waiting);
        Appointment started = service.startVisit("appt2", "doctor-1");
        assertThat(started.getStatus()).isEqualTo(AppointmentStatus.IN_VISIT);
    }

    @Test
    void skipDelegatesToMoveToTail() {
        Appointment appt = appointment("appt-1", "doctor-1", "patient-1", AppointmentStatus.WAITING, PaymentStatus.PAID);
        when(appointments.findById("appt-1")).thenReturn(Optional.of(appt));
        when(appointments.moveToTail("appt-1")).thenReturn(appt);

        service.skip("appt-1", "doctor-1");

        verify(appointments).moveToTail("appt-1");
    }

    @Test
    void lockOnlineDefaultsMorningStartTimeWhenBlank() {
        AppointmentController.CreateAppointmentRequest request = request(LocalDate.now().plusDays(1), "");
        when(appointments.existsActiveAtStartTime("patient-1", request.visitDate(), LocalTime.of(8, 0))).thenReturn(false);
        when(slots.tryLock("slot-1")).thenReturn(true);
        when(appointments.nextQueueNumber("doctor-1", request.visitDate())).thenReturn(2);
        when(appointments.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Appointment created = service.lockOnline(request);

        assertThat(created.getStartTime()).isEqualTo(LocalTime.of(8, 0));
    }

    @Test
    void syncSlotCreatesOrResizesInventory() {
        when(slots.findByScheduleId("slot-1")).thenReturn(Optional.empty());
        when(slots.save(any(SlotInventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SlotInventory created = service.syncSlot("slot-1", 8);

        assertThat(created.getSlotId()).isEqualTo("slot-1");
        assertThat(created.getCapacity()).isEqualTo(8);
        assertThat(created.getBooked()).isEqualTo(0);
    }

    @Test
    void syncSlotsFiltersBlankScheduleIds() {
        List<AppointmentController.SyncSlotRequest> requests = List.of(
                new AppointmentController.SyncSlotRequest("slot-1", 8),
                new AppointmentController.SyncSlotRequest("", 10),
                new AppointmentController.SyncSlotRequest("slot-2", 12));

        List<SlotInventory> synced = service.syncSlots(requests);

        assertThat(synced).extracting(SlotInventory::getSlotId).containsExactly("slot-1", "slot-2");
        verify(slots).saveAllCapacities(any());
    }

    @Test
    void syncSlotsReturnsEmptyForNullOrEmptyInput() {
        assertThat(service.syncSlots(null)).isEmpty();
        assertThat(service.syncSlots(List.of())).isEmpty();
        verify(slots, never()).saveAllCapacities(any());
    }

    @Test
    void releaseExpiredLocksDelegatesEachPendingId() {
        AppointmentService spyService = spy(service);
        when(appointments.findExpiredPendingIds()).thenReturn(List.of("appt-1", "appt-2"));
        doNothing().when(spyService).expireOne(anyString());

        spyService.releaseExpiredLocks();

        verify(spyService).expireOne("appt-1");
        verify(spyService).expireOne("appt-2");
    }

    @Test
    void expireOneIgnoresMissingOrNonPendingAppointments() {
        when(appointments.findByIdForUpdate("missing")).thenReturn(Optional.empty());
        service.expireOne("missing");
        verify(slots, never()).releaseLocked(anyString());

        Appointment waiting = appointment("waiting", "doctor-1", "patient-1", AppointmentStatus.WAITING, PaymentStatus.PAID);
        when(appointments.findByIdForUpdate("waiting")).thenReturn(Optional.of(waiting));
        service.expireOne("waiting");
        verify(appointments, never()).save(waiting);
    }

    @Test
    void failPaymentChecksPatientStatusAndReleasesLock() {
        Appointment pending = appointment("appt", "doctor-1", "patient-1", AppointmentStatus.PENDING_PAYMENT, PaymentStatus.UNPAID);
        when(appointments.findByIdForUpdate("appt")).thenReturn(Optional.of(pending));
        when(appointments.save(pending)).thenReturn(pending);

        Appointment failed = service.failPayment("appt", "patient-1");

        assertThat(failed.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(slots).releaseLocked("slot-1");
    }

    @Test
    void failPaymentRejectsDifferentPatient() {
        when(appointments.findByIdForUpdate("appt")).thenReturn(Optional.of(appointment()));

        assertThatThrownBy(() -> service.failPayment("appt", "patient-2"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void failPaymentReturnsAlreadyFailedAppointment() {
        Appointment failed = appointment("appt", "doctor-1", "patient-1", AppointmentStatus.CANCELLED, PaymentStatus.FAILED);
        when(appointments.findByIdForUpdate("appt")).thenReturn(Optional.of(failed));

        assertThat(service.failPayment("appt", "patient-1")).isSameAs(failed);
        verify(slots, never()).releaseLocked(anyString());
    }

    @Test
    void failPaymentRejectsNonPendingStatus() {
        Appointment waiting = appointment("appt", "doctor-1", "patient-1", AppointmentStatus.WAITING, PaymentStatus.UNPAID);
        when(appointments.findByIdForUpdate("appt")).thenReturn(Optional.of(waiting));

        assertThatThrownBy(() -> service.failPayment("appt", "patient-1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void enterRevisitReturnsSameWhenAlreadyWaiting() {
        Appointment revisit = appointment("appt", "doctor-1", "patient-1", AppointmentStatus.REVISIT_WAITING, PaymentStatus.PAID);
        when(appointments.findById("appt")).thenReturn(Optional.of(revisit));

        assertThat(service.enterRevisit("appt")).isSameAs(revisit);
        verify(appointments, never()).insertForRevisit(anyString(), anyInt());
    }

    @Test
    void findThrowsWhenAppointmentMissing() {
        when(appointments.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.find("missing")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schedulingHistorySummaryDelegatesToRepository() {
        AppointmentRepository.SchedulingHistorySummary summary =
                new AppointmentRepository.SchedulingHistorySummary(90, 201, true, List.of(), List.of(), List.of());
        when(appointments.schedulingHistorySummary(90)).thenReturn(summary);

        assertThat(service.schedulingHistorySummary(90)).isSameAs(summary);
    }

    private Appointment appointment() {
        return appointment("appt", "doctor-1", "patient-1", AppointmentStatus.PENDING_PAYMENT, PaymentStatus.UNPAID);
    }

    private Appointment appointment(String id, String doctorId, String patientId, AppointmentStatus status, PaymentStatus paymentStatus) {
        return appointment(id, doctorId, patientId, status, paymentStatus, LocalDate.now().plusDays(1), 1);
    }

    private Appointment appointment(
            String id,
            String doctorId,
            String patientId,
            AppointmentStatus status,
            PaymentStatus paymentStatus,
            LocalDate visitDate,
            int queueNumber) {
        return new Appointment(
                id,
                "slot-1",
                patientId,
                "Patient",
                doctorId,
                "Doctor",
                "dept-1",
                "Dept",
                visitDate,
                "MORNING",
                LocalTime.of(8, 0),
                AppointmentSource.ONLINE,
                status,
                paymentStatus,
                null,
                "LOW",
                null,
                queueNumber);
    }

    private AppointmentController.CreateAppointmentRequest request(LocalDate visitDate, String startTime) {
        return new AppointmentController.CreateAppointmentRequest(
                "slot-1",
                "patient-1",
                "Patient",
                "doctor-1",
                "Doctor",
                "dept-1",
                "Dept",
                visitDate.toString(),
                "MORNING",
                startTime,
                null,
                "LOW",
                null,
                new BigDecimal("12.30"));
    }
}
