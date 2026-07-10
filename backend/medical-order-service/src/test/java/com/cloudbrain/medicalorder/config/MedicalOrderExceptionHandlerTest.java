package com.cloudbrain.medicalorder.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MedicalOrderExceptionHandlerTest {
    private final MedicalOrderExceptionHandler handler = new MedicalOrderExceptionHandler();

    @Test
    void handleReturnsMessageMap() {
        assertThat(handler.handle(new IllegalArgumentException("bad request")))
                .containsEntry("message", "bad request");
    }
}
