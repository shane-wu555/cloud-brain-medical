package com.cloudbrain.patient.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DateTimeException;
import org.junit.jupiter.api.Test;

class PatientExceptionHandlerTest {
    private final PatientExceptionHandler handler = new PatientExceptionHandler();

    @Test
    void badRequestWrapsIllegalArgumentException() {
        var error = handler.badRequest(new IllegalArgumentException("bad input"));

        assertThat(error.code()).isEqualTo("PATIENT_REQUEST_INVALID");
        assertThat(error.message()).isEqualTo("bad input");
    }

    @Test
    void badRequestWrapsDateTimeException() {
        var error = handler.badRequest(new DateTimeException("bad date"));

        assertThat(error.code()).isEqualTo("PATIENT_REQUEST_INVALID");
        assertThat(error.message()).isEqualTo("bad date");
    }
}
