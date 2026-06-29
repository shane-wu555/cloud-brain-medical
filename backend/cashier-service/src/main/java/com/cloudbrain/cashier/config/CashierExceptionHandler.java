package com.cloudbrain.cashier.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

@RestControllerAdvice
public class CashierExceptionHandler {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBusinessException(RuntimeException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(RestClientResponseException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleDownstreamException(RestClientResponseException exception) {
        return Map.of("message", downstreamMessage(exception));
    }

    private String downstreamMessage(RestClientResponseException exception) {
        String body = exception.getResponseBodyAsString(StandardCharsets.UTF_8);
        if (body == null || body.isBlank()) {
            return "下游业务确认失败";
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            String message = root.path("message").asText();
            return message == null || message.isBlank() ? body : message;
        } catch (Exception ignored) {
            return body;
        }
    }
}
