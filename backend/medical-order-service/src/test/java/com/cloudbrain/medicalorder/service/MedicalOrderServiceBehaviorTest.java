package com.cloudbrain.medicalorder.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

class MedicalOrderServiceBehaviorTest {
    private final MedicalOrderRepository repository = Mockito.mock(MedicalOrderRepository.class);
    private final AiTriageClient triageClient = Mockito.mock(AiTriageClient.class);
    private final AuditPublisher auditPublisher = Mockito.mock(AuditPublisher.class);
    private final MedicalOrderService service = new MedicalOrderService(repository, triageClient, auditPublisher);

    @Test
    void createBuildsOrderWithDefaultUrgencyAndZeroAmount() {
        when(repository.existsActiveOrder("appt-1", "CT")).thenReturn(false);
        when(repository.create(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MedicalOrder created = service.create(
                new MedicalOrderController.CreateRequest("appt-1", "patient-1", "Patient", "CHECK", "CT", "CT", "purpose", "HEAD", null, null),
                "doctor-1");

        assertThat(created.orderingDoctorId()).isEqualTo("doctor-1");
        assertThat(created.urgency()).isEqualTo("ROUTINE");
        assertThat(created.amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void createRejectsInvalidUrgencyAndDuplicateActiveOrder() {
        when(repository.existsActiveOrder("appt-1", "CT")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                        new MedicalOrderController.CreateRequest("appt-1", "patient-1", "Patient", "CHECK", "CT", "CT", "purpose", "HEAD", BigDecimal.ONE, "INVALID"),
                        "doctor-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(
                        new MedicalOrderController.CreateRequest("appt-1", "patient-1", "Patient", "CHECK", "CT", "CT", "purpose", "HEAD", BigDecimal.ONE, "ROUTINE"),
                        "doctor-1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void payAssignsRoomAndReturnsUpdatedOrder() {
        MedicalOrder pending = order("order-1", "CHECK", "patient-1", "doctor-1", "PENDING_PAYMENT", null, "UNPAID");
        MedicalOrder paid = order("order-1", "CHECK", "patient-1", "doctor-1", "WAITING_TRIAGE", null, "PAID");
        MedicalOrder assigned = order("order-1", "CHECK", "patient-1", "doctor-1", "WAITING", "room-1", "PAID");
        when(repository.findById("order-1")).thenReturn(Optional.of(pending), Optional.of(paid), Optional.of(assigned));
        when(repository.markPaid("order-1")).thenReturn(true);
        when(repository.roomCandidates("CHECK", "ITEM")).thenReturn(List.of(new MedicalOrderRepository.RoomCandidate("room-1", "Room 1", "CT", "Floor 1", "CT-1", 10, 1)));
        when(triageClient.triage(any(), any())).thenReturn(new AiTriageClient.TriageResult("room-1", "AI", "matched"));
        when(repository.assign("order-1", "room-1", "AI", "matched")).thenReturn(true);

        MedicalOrder result = service.pay("order-1", "cashier-1", "CASHIER");

        assertThat(result.status()).isEqualTo("WAITING");
    }

    @Test
    void missCallStartCompleteAndReportPendingCoverSuccessAndGuards() {
        MedicalOrder waiting = order("order-1", "CHECK", "patient-1", "doctor-1", "WAITING", "room-1", "PAID");
        MedicalOrder called = order("order-1", "CHECK", "patient-1", "doctor-1", "CALLED", "room-1", "PAID");
        MedicalOrder inProgress = order("order-1", "CHECK", "patient-1", "doctor-1", "IN_PROGRESS", "room-1", "PAID");
        MedicalOrder completed = order("order-1", "CHECK", "patient-1", "doctor-1", "COMPLETED", "room-1", "PAID");
        when(repository.findById("order-1")).thenReturn(
                Optional.of(waiting),
                Optional.of(waiting),
                Optional.of(called),
                Optional.of(called),
                Optional.of(inProgress),
                Optional.of(inProgress),
                Optional.of(inProgress),
                Optional.of(inProgress),
                Optional.of(completed));
        when(repository.staffRoom("checker-1")).thenReturn(Optional.of(new MedicalOrderRepository.StaffRoom("checker-1", "room-1")));
        when(repository.call("order-1", "room-1")).thenReturn(true);
        when(repository.start("order-1", "room-1", "checker-1")).thenReturn(true);
        when(repository.complete("order-1", "room-1", "checker-1", "done", "HUMAN", null)).thenReturn(true);
        when(repository.markReportPending(org.mockito.ArgumentMatchers.eq("order-1"), org.mockito.ArgumentMatchers.eq("room-1"),
                org.mockito.ArgumentMatchers.eq("checker-1"), any())).thenReturn(true);

        service.miss("order-1", "checker-1", "CHECK_DOCTOR");
        assertThat(service.call("order-1", "checker-1", "CHECK_DOCTOR")).isNotNull();
        assertThat(service.start("order-1", "checker-1", "CHECK_DOCTOR")).isNotNull();
        service.markReportPending("order-1", "checker-1", "CHECK_DOCTOR", null);
        assertThat(service.complete("order-1", "checker-1", "CHECK_DOCTOR", "done", "HUMAN", null)).isNotNull();

        verify(repository).moveToTail("order-1", "room-1");
        assertThatThrownBy(() -> service.complete("order-1", "checker-1", "CHECK_DOCTOR", "done", "AI", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listAuthorizedFiltersByRoleRoomAndViewAndPublishesAudit() {
        MedicalOrder disposalWaiting = order("order-1", "DISPOSAL", "patient-1", "doctor-1", "WAITING", "room-1", "PAID");
        MedicalOrder disposalCompleted = order("order-2", "DISPOSAL", "patient-1", "doctor-1", "COMPLETED", "room-1", "PAID");
        when(repository.find("DISPOSAL", null, "patient-1", null)).thenReturn(List.of(disposalWaiting, disposalCompleted));
        when(repository.staffRoom("disposal-1")).thenReturn(Optional.of(new MedicalOrderRepository.StaffRoom("disposal-1", "room-1")));

        List<MedicalOrder> visible = service.listAuthorized("DISPOSAL", null, "patient-1", null, "DISPOSAL_RECORD", "disposal-1", "DISPOSAL_DOCTOR");

        assertThat(visible).containsExactly(disposalCompleted);
        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditPublisher).publish(
                org.mockito.ArgumentMatchers.eq("MEDICAL_ORDER_LIST_VIEW"),
                org.mockito.ArgumentMatchers.eq("MEDICAL_ORDER"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("patient-1"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("disposal-1"),
                org.mockito.ArgumentMatchers.eq("DISPOSAL_DOCTOR"),
                detailsCaptor.capture());
        assertThat(detailsCaptor.getValue()).containsEntry("view", "DISPOSAL_RECORD");
    }

    @Test
    void patientPayOwnershipAndRoleChecksRejectInvalidActors() {
        MedicalOrder order = order("order-1", "CHECK", "patient-1", "doctor-1", "PENDING_PAYMENT", "room-1", "UNPAID");
        when(repository.findById("order-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.pay("order-1", "patient-2", "PATIENT"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.start("order-1", "checker-1", "LAB_DOCTOR"))
                .isInstanceOf(IllegalStateException.class);
    }

    private MedicalOrder order(String id, String type, String patientId, String doctorId, String status, String roomId, String paymentStatus) {
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
