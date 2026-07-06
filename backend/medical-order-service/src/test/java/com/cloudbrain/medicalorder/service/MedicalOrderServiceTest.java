package com.cloudbrain.medicalorder.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.medicalorder.audit.AuditPublisher;
import com.cloudbrain.medicalorder.domain.MedicalOrder;
import com.cloudbrain.medicalorder.repository.MedicalOrderRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MedicalOrderServiceTest {
    @Test
    void rejectsDoctorRoleThatDoesNotMatchOrderType() {
        MedicalOrderRepository repository = Mockito.mock(MedicalOrderRepository.class);
        MedicalOrder order = order("LAB", "PAID", "WAITING");
        when(repository.findById("order")).thenReturn(Optional.of(order));
        MedicalOrderService service = new MedicalOrderService(
                repository,
                Mockito.mock(AiTriageClient.class),
                Mockito.mock(AuditPublisher.class));
        assertThrows(IllegalStateException.class, () -> service.start("order", "check-doctor", "CHECK_DOCTOR"));
    }

    @Test
    void disposalRecordListPublishesSensitiveAccessAudit() {
        MedicalOrderRepository repository = Mockito.mock(MedicalOrderRepository.class);
        AuditPublisher auditPublisher = Mockito.mock(AuditPublisher.class);
        MedicalOrderService service = new MedicalOrderService(
                repository,
                Mockito.mock(AiTriageClient.class),
                auditPublisher);
        when(repository.find("DISPOSAL", null, "patient-1", null)).thenReturn(List.of(
                order("DISPOSAL", "PAID", "COMPLETED"),
                order("DISPOSAL", "PAID", "WAITING")));

        List<MedicalOrder> orders = service.listAuthorized(
                "DISPOSAL",
                null,
                "patient-1",
                null,
                "DISPOSAL_RECORD",
                "patient-account-1",
                "PATIENT");

        assertThat(orders).hasSize(1);
        verify(auditPublisher).publish(
                eq("MEDICAL_ORDER_LIST_VIEW"),
                eq("MEDICAL_ORDER"),
                isNull(),
                eq("patient-1"),
                isNull(),
                eq("patient-account-1"),
                eq("PATIENT"),
                eq(Map.of(
                        "accessScope", "LIST",
                        "typeFilter", "DISPOSAL",
                        "view", "DISPOSAL_RECORD",
                        "auditSummary", "查看了处置记录",
                        "resultCount", 1)));
    }

    @Test
    void paymentRecordListAuditsRelatedDisposalCountWhenPresent() {
        MedicalOrderRepository repository = Mockito.mock(MedicalOrderRepository.class);
        AuditPublisher auditPublisher = Mockito.mock(AuditPublisher.class);
        MedicalOrderService service = new MedicalOrderService(
                repository,
                Mockito.mock(AiTriageClient.class),
                auditPublisher);
        when(repository.find(null, null, "patient-1", null)).thenReturn(List.of(
                order("DISPOSAL", "PAID", "COMPLETED"),
                order("CHECK", "PAID", "COMPLETED")));

        List<MedicalOrder> orders = service.listAuthorized(
                null,
                null,
                "patient-1",
                null,
                "PAYMENT_RECORD",
                "patient-account-1",
                "PATIENT");

        assertThat(orders).hasSize(2);
        verify(auditPublisher).publish(
                eq("MEDICAL_ORDER_LIST_VIEW"),
                eq("MEDICAL_ORDER"),
                isNull(),
                eq("patient-1"),
                isNull(),
                eq("patient-account-1"),
                eq("PATIENT"),
                eq(Map.of(
                        "accessScope", "LIST",
                        "view", "PAYMENT_RECORD",
                        "auditSummary", "查看了缴费退费记录（含处置信息）",
                        "relatedDisposalCount", 1L,
                        "resultCount", 2)));
    }

    private static MedicalOrder order(String type, String paymentStatus, String status) {
        return new MedicalOrder(
                "order", "appointment", "patient-1", "患者", "doctor",
                type, "ITEM", "项目", null, null,
                BigDecimal.ZERO, paymentStatus, status,
                "rm-lab-01", null, null,
                null, 1, "ROUTINE",
                null, null, 0,
                null, null, null, null, null,
                LocalDateTime.now(), null, status.equals("COMPLETED") ? LocalDateTime.now() : null);
    }
}
