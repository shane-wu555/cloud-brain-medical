package com.cloudbrain.doctor.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class DoctorExceptionHandlerTest {
    private final DoctorExceptionHandler handler = new DoctorExceptionHandler();

    @Test
    void responseStatusExceptionMapsToUpstreamError() {
        var response = handler.handleResponseStatus(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "gateway down"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("DOCTOR_UPSTREAM_ERROR");
        assertThat(response.getBody().message()).isEqualTo("gateway down");
    }

    @Test
    void businessExceptionMapsToBadRequest() {
        var response = handler.handleBusinessException(new IllegalArgumentException("bad request"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("DOCTOR_REQUEST_INVALID");
        assertThat(response.getBody().message()).isEqualTo("bad request");
    }
}
