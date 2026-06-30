package com.cloudbrain.doctor.config;

import com.cloudbrain.common.api.ApiError;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class DoctorExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException exception) {
        String reason = exception.getReason();
        return ResponseEntity.status(exception.getStatusCode())
                .body(ApiError.of("DOCTOR_UPSTREAM_ERROR", reason == null ? "Upstream service call failed" : reason));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiError> handleBusinessException(RuntimeException exception) {
        return ResponseEntity.badRequest().body(ApiError.of("DOCTOR_REQUEST_INVALID", exception.getMessage()));
    }
}
