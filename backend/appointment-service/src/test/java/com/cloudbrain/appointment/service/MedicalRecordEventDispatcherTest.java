package com.cloudbrain.appointment.service;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.appointment.repository.MedicalRecordEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MedicalRecordEventDispatcherTest {
    @Mock MedicalRecordEventRepository repository;
    @Mock MedicalRecordClient medicalRecordClient;
    @Mock CashierClient cashierClient;
    MedicalRecordEventDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new MedicalRecordEventDispatcher(repository, medicalRecordClient, cashierClient, new ObjectMapper());
    }

    @Test
    void dispatchRoutesSupportedEventsAndMarksCompleted() {
        when(repository.findPending(20)).thenReturn(List.of(
                new MedicalRecordEventRepository.PendingEvent("evt-1", "APPOINTMENT_PAID", "{\"appointmentId\":\"a1\"}", 0),
                new MedicalRecordEventRepository.PendingEvent("evt-2", "PAYMENT_COMPLETED", "{\"amount\":12}", 0),
                new MedicalRecordEventRepository.PendingEvent("evt-3", "REFUND_COMPLETED", "{\"amount\":6}", 0)));

        dispatcher.dispatch();

        verify(medicalRecordClient).createInitialRecord(anyMap());
        verify(cashierClient).recordPayment(anyMap());
        verify(cashierClient).recordRefund(anyMap());
        verify(repository).markCompleted("evt-1");
        verify(repository).markCompleted("evt-2");
        verify(repository).markCompleted("evt-3");
    }

    @Test
    void dispatchMarksUnknownEventAsFailed() {
        when(repository.findPending(20)).thenReturn(List.of(
                new MedicalRecordEventRepository.PendingEvent("evt-1", "UNKNOWN", "{\"key\":\"value\"}", 4)));

        dispatcher.dispatch();

        verify(repository).markFailed("evt-1", 4, "不支持的集成事件: UNKNOWN");
        verify(repository, never()).markCompleted("evt-1");
    }

    @Test
    void dispatchMarksClientFailureAsFailed() {
        when(repository.findPending(20)).thenReturn(List.of(
                new MedicalRecordEventRepository.PendingEvent("evt-1", "APPOINTMENT_PAID", "{\"appointmentId\":\"a1\"}", 1)));
        doThrow(new IllegalStateException("create failed")).when(medicalRecordClient).createInitialRecord(anyMap());

        dispatcher.dispatch();

        verify(repository).markFailed("evt-1", 1, "create failed");
    }
}
