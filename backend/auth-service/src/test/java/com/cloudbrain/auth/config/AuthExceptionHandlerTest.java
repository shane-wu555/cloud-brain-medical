package com.cloudbrain.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class AuthExceptionHandlerTest {
    private final AuthExceptionHandler handler = new AuthExceptionHandler();

    @Test
    void businessExceptionMapsToApiError() {
        var error = handler.handleBusinessException(new IllegalArgumentException("bad request"));

        assertThat(error.code()).isEqualTo("AUTH_REQUEST_INVALID");
        assertThat(error.message()).isEqualTo("bad request");
    }

    @Test
    void validationExceptionUsesFirstFieldError() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "phone", "must not be blank"));
        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(mock(MethodParameter.class), bindingResult);

        var error = handler.handleValidation(exception);

        assertThat(error.code()).isEqualTo("VALIDATION_FAILED");
        assertThat(error.message()).isEqualTo("phone: must not be blank");
    }
}
