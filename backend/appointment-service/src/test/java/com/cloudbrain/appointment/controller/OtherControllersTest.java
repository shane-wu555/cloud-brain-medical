package com.cloudbrain.appointment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.appointment.entity.Appointment;
import com.cloudbrain.appointment.entity.AppointmentSource;
import com.cloudbrain.appointment.entity.AppointmentStatus;
import com.cloudbrain.appointment.entity.PaymentStatus;
import com.cloudbrain.appointment.entity.SlotInventory;
import com.cloudbrain.appointment.repository.AppointmentRepository;
import com.cloudbrain.appointment.repository.MedicalRecordEventRepository;
import com.cloudbrain.appointment.service.AppointmentService;
import com.cloudbrain.appointment.support.TestHttpServer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class OtherControllersTest {
    @Mock AppointmentService appointmentService;
    @Mock MedicalRecordEventRepository eventRepository;
    @Mock AppointmentRepository appointmentRepository;

    @Test
    void internalAppointmentControllerChecksApiKeyAndDelegates() {
        InternalAppointmentController controller = new InternalAppointmentController(appointmentService, "secret");
        Appointment appointment = appointment("appt", AppointmentStatus.WAITING, PaymentStatus.PAID);
        when(appointmentService.pay("appt", "WECHAT", new BigDecimal("0.01"), "patient-1")).thenReturn(appointment);
        when(appointmentService.failPayment("appt", "patient-1")).thenReturn(appointment);
        when(appointmentService.cancel("appt", true)).thenReturn(appointment);
        when(appointmentService.enterRevisit("appt")).thenReturn(appointment);
        AppointmentRepository.SchedulingHistorySummary summary =
                new AppointmentRepository.SchedulingHistorySummary(90, 220, true, List.of(), List.of(), List.of());
        when(appointmentService.schedulingHistorySummary(90)).thenReturn(summary);

        assertThat(controller.confirm("appt", "secret",
                new InternalAppointmentController.PaymentConfirmation("patient-1", "WECHAT", "po-1"))).isSameAs(appointment);
        assertThat(controller.fail("appt", "secret",
                new InternalAppointmentController.PaymentFailure("patient-1", "po-1"))).isSameAs(appointment);
        assertThat(controller.refund("appt", "secret",
                new InternalAppointmentController.RefundConfirmation("patient-1", "cashier-1"))).isSameAs(appointment);
        assertThat(controller.revisit("appt", "secret")).isSameAs(appointment);
        assertThat(controller.schedulingHistorySummary("secret", 90)).isSameAs(summary);

        assertThatThrownBy(() -> controller.confirm("appt", "bad",
                new InternalAppointmentController.PaymentConfirmation("patient-1", "WECHAT", "po-1")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void internalSlotControllerChecksApiKeyAndDelegates() {
        InternalSlotController controller = new InternalSlotController(appointmentService, "secret");
        SlotInventory inventory = new SlotInventory("slot-1", 10, 2);
        when(appointmentService.syncSlot("slot-1", 8)).thenReturn(inventory);
        when(appointmentService.syncSlots(List.of(new AppointmentController.SyncSlotRequest("slot-1", 8))))
                .thenReturn(List.of(inventory));
        when(appointmentService.slots()).thenReturn(List.of(inventory));

        assertThat(controller.sync("secret", new AppointmentController.SyncSlotRequest("slot-1", 8))).isSameAs(inventory);
        assertThat(controller.syncBatch("secret", List.of(new AppointmentController.SyncSlotRequest("slot-1", 8))))
                .containsExactly(inventory);
        assertThat(controller.list("secret")).containsExactly(inventory);

        assertThatThrownBy(() -> controller.list("bad")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void integrationEventControllerListsAndRetries() {
        IntegrationEventController controller = new IntegrationEventController(eventRepository);
        MedicalRecordEventRepository.EventView view = new MedicalRecordEventRepository.EventView(
                "evt-1", "appt-1", "APPOINTMENT_PAID", "FAILED", 2,
                LocalDateTime.now(), "boom", LocalDateTime.now(), null);
        when(eventRepository.findEvents("FAILED")).thenReturn(List.of(view));

        assertThat(controller.list("FAILED")).containsExactly(view);
        controller.retry("evt-1");
        verify(eventRepository).retry("evt-1");
    }

    @Test
    void dashboardControllerBuildsOverviewFromRepositoryAndDoctorService() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.enqueueJson(200, "{\"activeDoctors\":\"3\",\"scheduledRooms\":2,\"totalRooms\":\"oops\",\"roomCoverageRate\":null}");
            DashboardController controller = new DashboardController(appointmentRepository, server.baseUrl(), "secret");
            when(appointmentRepository.dashboardStats(LocalDate.now())).thenReturn(
                    new AppointmentRepository.DashboardAppointmentStats(
                            12, 5, 4, List.of(new AppointmentRepository.DepartmentLoad("Cardiology", 7))));

            Map<String, Object> overview = controller.overview();

            assertThat(overview.get("todayAppointments")).isEqualTo(12);
            assertThat(overview.get("waitingVisits")).isEqualTo(5);
            assertThat(overview.get("activeDoctors")).isEqualTo(3);
            assertThat(overview.get("scheduledRooms")).isEqualTo(2);
            assertThat(overview.get("totalRooms")).isEqualTo(0);
            assertThat(overview.get("roomCoverageRate")).isEqualTo(0);
            assertThat(server.requests().get(0).headers().getFirst("X-Internal-Api-Key")).isEqualTo("secret");
        }
    }

    @Test
    void dashboardControllerFallsBackWhenDoctorServiceUnavailable() {
        DashboardController controller = new DashboardController(appointmentRepository, "http://127.0.0.1:9", "secret");
        when(appointmentRepository.dashboardStats(LocalDate.now())).thenReturn(
                new AppointmentRepository.DashboardAppointmentStats(12, 5, 4, List.of()));

        Map<String, Object> overview = controller.overview();

        assertThat(overview.get("activeDoctors")).isEqualTo(0);
        assertThat(overview.get("scheduledRooms")).isEqualTo(0);
        assertThat(overview.get("totalRooms")).isEqualTo(0);
        assertThat(overview.get("roomCoverageRate")).isEqualTo(0);
    }

    private Appointment appointment(String id, AppointmentStatus status, PaymentStatus paymentStatus) {
        return new Appointment(
                id,
                "slot-1",
                "patient-1",
                "Patient",
                "doctor-1",
                "Doctor",
                "dept-1",
                "Dept",
                LocalDate.now().plusDays(1),
                "MORNING",
                LocalTime.of(8, 0),
                AppointmentSource.ONLINE,
                status,
                paymentStatus,
                null,
                "LOW",
                null,
                1);
    }
}
