package com.cloudbrain.medicalrecord.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

class MedicalRecordExceptionHandlerTest {
    private final MedicalRecordExceptionHandler handler = new MedicalRecordExceptionHandler();

    @Test
    void businessExceptionReturnsMessageMap() {
        assertThat(handler.handleBusinessException(new IllegalArgumentException("bad request")))
                .containsEntry("message", "bad request");
    }

    @Test
    void optimisticLockExceptionReturnsConflictMessage() {
        assertThat(handler.handleConflict(new OptimisticLockingFailureException("conflict")))
                .containsEntry("message", "conflict");
    }
}
