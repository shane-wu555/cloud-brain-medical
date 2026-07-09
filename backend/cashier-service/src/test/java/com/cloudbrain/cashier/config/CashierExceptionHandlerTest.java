package com.cloudbrain.cashier.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;

class CashierExceptionHandlerTest {
    private final CashierExceptionHandler handler = new CashierExceptionHandler();

    @Test
    void handleBusinessExceptionReturnsMessageBody() {
        assertThat(handler.handleBusinessException(new IllegalArgumentException("bad request")))
                .isEqualTo(java.util.Map.of("message", "bad request"));
    }

    @Test
    void handleDownstreamExceptionPrefersJsonMessageField() {
        RestClientResponseException exception = new RestClientResponseException(
                "downstream failed",
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                HttpHeaders.EMPTY,
                "{\"message\":\"payment rejected\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.charset.StandardCharsets.UTF_8);

        assertThat(handler.handleDownstreamException(exception))
                .isEqualTo(java.util.Map.of("message", "payment rejected"));
    }

    @Test
    void handleDownstreamExceptionFallsBackToRawBody() {
        RestClientResponseException exception = new RestClientResponseException(
                "downstream failed",
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                HttpHeaders.EMPTY,
                "plain body".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.charset.StandardCharsets.UTF_8);

        assertThat(handler.handleDownstreamException(exception))
                .isEqualTo(java.util.Map.of("message", "plain body"));
    }

    @Test
    void handleDownstreamExceptionUsesDefaultMessageForBlankBody() {
        RestClientResponseException exception = new RestClientResponseException(
                "downstream failed",
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                HttpHeaders.EMPTY,
                new byte[0],
                java.nio.charset.StandardCharsets.UTF_8);

        assertThat(handler.handleDownstreamException(exception))
                .containsKey("message");
        assertThat(handler.handleDownstreamException(exception).get("message")).isNotBlank();
    }
}
