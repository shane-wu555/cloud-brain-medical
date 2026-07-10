package com.cloudbrain.medicalorder.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.medicalorder.audit.AuditPublisher;
import com.cloudbrain.medicalorder.controller.MedicalOrderController;
import com.cloudbrain.medicalorder.domain.MedicalOrder;
import com.cloudbrain.medicalorder.repository.MedicalOrderRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

class MedicalOrderServiceBranchTest {
    private final MedicalOrderRepository repository = Mockito.mock(MedicalOrderRepository.class);
    private final AiTriageClient triageClient = Mockito.mock(AiTriageClient.class);
    private final AuditPublisher auditPublisher = Mockito.mock(AuditPublisher.class);
    private final MedicalOrderService service = new MedicalOrderService(repository, triageClient, auditPublisher);

    @Test
    void createValidatesTypeRequiredFieldsAndUrgency() {
        assertThatThrownBy(() -> service.create(
                        new MedicalOrderController.CreateRequest("appt", "patient", "Patient", "OTHER", "ITEM", "Item", "purpose", "HEAD", BigDecimal.ONE, "ROUTINE"),
                        "doctor"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(
                        new MedicalOrderController.CreateRequest("", "patient", "Patient", "CHECK", "ITEM", "Item", "purpose", "HEAD", BigDecimal.ONE, "ROUTINE"),
                        "doctor"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(
                        new MedicalOrderController.CreateRequest("appt", "patient", "Patient", "CHECK", "ITEM", "Item", "purpose", "HEAD", BigDecimal.ONE, "LATER"),
                        "doctor"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listAuthorizedCoversTechnicianDoctorAndPaymentViews() {
        MedicalOrder labMine = order("order-1", "LAB", "WAITING", "room-1", "doctor-1", "PAID");
        MedicalOrder labOtherRoom = order("order-2", "LAB", "WAITING", "room-2", "doctor-1", "PAID");
        when(repository.find("LAB", null, "patient-1", null)).thenReturn(List.of(labMine, labOtherRoom));
        when(repository.staffRoom("lab-1")).thenReturn(Optional.of(new MedicalOrderRepository.StaffRoom("lab-1", "room-1")));

        List<MedicalOrder> labVisible = service.listAuthorized(
                "CHECK",
                null,
                "patient-1",
                null,
                " ",
                "lab-1",
                "LAB_DOCTOR");

        assertThat(labVisible).containsExactly(labMine);

        MedicalOrder disposalMine = order("order-3", "DISPOSAL", "COMPLETED", "room-3", "patient-1", "PAID");
        MedicalOrder outpatientMine = order("order-4", "CHECK", "WAITING", "room-4", "patient-1", "UNPAID");
        MedicalOrder outpatientOther = orderForDoctor("order-5", "CHECK", "WAITING", "room-4", "patient-1", "doctor-2", "PAID");
        when(repository.find(null, null, "patient-1", null)).thenReturn(List.of(disposalMine, outpatientMine, outpatientOther));

        List<MedicalOrder> doctorVisible = service.listAuthorized(
                null,
                null,
                "patient-1",
                null,
                "OUTPATIENT_PAYMENT",
                "doctor-1",
                "OUTPATIENT_DOCTOR");

        assertThat(doctorVisible).containsExactly(disposalMine, outpatientMine);
        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditPublisher, atLeastOnce()).publish(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                detailsCaptor.capture());
        assertThat(detailsCaptor.getAllValues()).anySatisfy(details ->
                assertThat(details).containsEntry("view", "OUTPATIENT_PAYMENT"));
    }

    @Test
    void listAuthorizedReturnsEmptyWhenTechnicianHasNoBoundRoom() {
        MedicalOrder waiting = order("order-6", "CHECK", "WAITING", "room-1", "patient-1", "PAID");
        when(repository.find("CHECK", null, "patient-1", null)).thenReturn(List.of(waiting));
        when(repository.staffRoom("checker-9")).thenReturn(Optional.empty());

        assertThat(service.listAuthorized(
                        "CHECK",
                        null,
                        "patient-1",
                        null,
                        "",
                        "checker-9",
                        "CHECK_DOCTOR"))
                .isEmpty();
    }

    @Test
    void payValidatesTransitionAndAssignmentRace() {
        when(repository.findById("order-1")).thenReturn(Optional.of(order("order-1", "CHECK", "PENDING_PAYMENT", null, "patient-1", "UNPAID")));
        when(repository.markPaid("order-1")).thenReturn(false);
        assertThatThrownBy(() -> service.pay("order-1", "cashier-1", "CASHIER"))
                .isInstanceOf(IllegalStateException.class);

        MedicalOrder pending = order("order-2", "CHECK", "PENDING_PAYMENT", null, "patient-1", "UNPAID");
        MedicalOrder paid = order("order-2", "CHECK", "WAITING_TRIAGE", null, "patient-1", "PAID");
        when(repository.findById("order-2")).thenReturn(Optional.of(pending), Optional.of(paid));
        when(repository.markPaid("order-2")).thenReturn(true);
        when(repository.roomCandidates("CHECK", "ITEM")).thenReturn(List.of(new MedicalOrderRepository.RoomCandidate("room-1", "Room 1", "ITEM", "Floor 1", "EQ", 1, 0)));
        when(triageClient.triage(any(), any())).thenReturn(new AiTriageClient.TriageResult("room-1", "AI", "matched"));
        when(repository.assign("order-2", "room-1", "AI", "matched")).thenReturn(false);

        assertThatThrownBy(() -> service.pay("order-2", "cashier-1", "CASHIER"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void executorOperationsValidateRoomSourceAndRepositoryState() {
        when(repository.findById("order-1")).thenReturn(Optional.of(order("order-1", "CHECK", "WAITING", "room-1", "patient-1", "PAID")));
        when(repository.staffRoom("checker-1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.miss("order-1", "checker-1", "CHECK_DOCTOR"))
                .isInstanceOf(AccessDeniedException.class);

        when(repository.staffRoom("checker-1")).thenReturn(Optional.of(new MedicalOrderRepository.StaffRoom("checker-1", "room-2")));
        assertThatThrownBy(() -> service.call("order-1", "checker-1", "CHECK_DOCTOR"))
                .isInstanceOf(AccessDeniedException.class);

        when(repository.findById("order-2")).thenReturn(Optional.of(order("order-2", "CHECK", "WAITING", "room-1", "patient-1", "PAID")));
        when(repository.staffRoom("checker-2")).thenReturn(Optional.of(new MedicalOrderRepository.StaffRoom("checker-2", "room-1")));
        when(repository.call("order-2", "room-1")).thenReturn(false);
        assertThatThrownBy(() -> service.call("order-2", "checker-2", "CHECK_DOCTOR"))
                .isInstanceOf(IllegalStateException.class);

        when(repository.findById("order-3")).thenReturn(Optional.of(order("order-3", "CHECK", "IN_PROGRESS", "room-1", "patient-1", "PAID")));
        when(repository.complete("order-3", "room-1", "checker-2", "summary", "HUMAN", null)).thenReturn(false);
        assertThatThrownBy(() -> service.complete("order-3", "checker-2", "CHECK_DOCTOR", "summary", "HUMAN", null))
                .isInstanceOf(IllegalStateException.class);

        when(repository.findById("order-4")).thenReturn(Optional.of(order("order-4", "CHECK", "IN_PROGRESS", "room-1", "patient-1", "PAID")));
        when(repository.markReportPending("order-4", "room-1", "checker-2", "custom")).thenReturn(false);
        assertThatThrownBy(() -> service.markReportPending("order-4", "checker-2", "CHECK_DOCTOR", "custom"))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> service.complete("order-4", "checker-2", "CHECK_DOCTOR", "summary", "BOT", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.complete("order-4", "checker-2", "CHECK_DOCTOR", "summary", "AI", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private MedicalOrder order(
            String id, String type, String status, String roomId, String patientId, String paymentStatus) {
        return orderForDoctor(id, type, status, roomId, patientId, "doctor-1", paymentStatus);
    }

    private MedicalOrder orderForDoctor(
            String id, String type, String status, String roomId, String patientId, String doctorId, String paymentStatus) {
        return new MedicalOrder(
                id,
                "appt-1",
                patientId,
                "Patient",
                doctorId,
                type,
                "ITEM",
                "Item",
                "purpose",
                "HEAD",
                BigDecimal.TEN,
                paymentStatus,
                status,
                roomId,
                roomId == null ? null : "Room 1",
                roomId == null ? null : "Floor 1",
                "staff-1",
                1,
                "ROUTINE",
                null,
                null,
                0,
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.now(),
                null,
                null);
    }
}
