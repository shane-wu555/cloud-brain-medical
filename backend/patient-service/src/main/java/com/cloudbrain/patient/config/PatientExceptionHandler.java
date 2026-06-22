package com.cloudbrain.patient.config;

import com.cloudbrain.common.api.ApiError;
import java.time.DateTimeException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PatientExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, DateTimeException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError badRequest(RuntimeException exception) {
        return ApiError.of("PATIENT_REQUEST_INVALID", exception.getMessage());
    }
}
