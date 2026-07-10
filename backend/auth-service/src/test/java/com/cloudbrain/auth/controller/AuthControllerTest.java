package com.cloudbrain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {
    @Mock
    AuthService authService;

    @Mock
    com.cloudbrain.auth.repository.UserAccountRepository accounts;

    @Mock
    HttpServletRequest servletRequest;

    @Test
    void registerUsesForwardedAddressWhenPresent() {
        AuthController controller = new AuthController(authService, accounts, "internal-key");
        AuthController.RegisterRequest request =
                new AuthController.RegisterRequest("13800000000", "Password1", "Alice", "123456");
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 10.0.0.2");
        when(servletRequest.getHeader("User-Agent")).thenReturn("browser");
        when(authService.register(request, new AuthService.ClientInfo("10.0.0.1", "browser")))
                .thenReturn(Map.of("token", "signed-token"));

        Map<String, Object> result = controller.register(request, servletRequest);

        assertThat(result).containsEntry("token", "signed-token");
    }

    @Test
    void loginFallsBackToRemoteAddress() {
        AuthController controller = new AuthController(authService, accounts, "internal-key");
        AuthController.LoginRequest request = new AuthController.LoginRequest("user-1", "Password1");
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn(" ");
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(servletRequest.getHeader("User-Agent")).thenReturn("agent");
        when(authService.login(request, new AuthService.ClientInfo("127.0.0.1", "agent")))
                .thenReturn(Map.of("token", "login-token"));

        Map<String, Object> result = controller.login(request, servletRequest);

        assertThat(result).containsEntry("token", "login-token");
    }

    @Test
    void controllerDelegatesRemainingAuthEndpoints() {
        AuthController controller = new AuthController(authService, accounts, "internal-key");
        AuthController.SendCodeRequest sendCodeRequest = new AuthController.SendCodeRequest("13800000000", "LOGIN");
        AuthController.SmsLoginRequest smsLoginRequest = new AuthController.SmsLoginRequest("13800000000", "654321");
        AuthController.ResetPasswordRequest resetPasswordRequest =
                new AuthController.ResetPasswordRequest("13800000000", "654321", "Password2");
        AuthController.ChangePasswordRequest changePasswordRequest =
                new AuthController.ChangePasswordRequest("Password1", "Password2");
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(servletRequest.getRemoteAddr()).thenReturn("192.168.0.1");
        when(servletRequest.getHeader("User-Agent")).thenReturn("test");
        when(authService.sendCode(sendCodeRequest, new AuthService.ClientInfo("192.168.0.1", "test")))
                .thenReturn(Map.of("expiresIn", 300));
        when(authService.smsLogin(smsLoginRequest, new AuthService.ClientInfo("192.168.0.1", "test")))
                .thenReturn(Map.of("token", "sms-token"));

        assertThat(controller.sendCode(sendCodeRequest, servletRequest)).containsEntry("expiresIn", 300);
        assertThat(controller.smsLogin(smsLoginRequest, servletRequest)).containsEntry("token", "sms-token");
        controller.resetPassword(resetPasswordRequest, servletRequest);
        controller.changePassword(changePasswordRequest, "user-1");

        verify(authService).resetPassword(resetPasswordRequest, new AuthService.ClientInfo("192.168.0.1", "test"));
        verify(authService).changePassword("user-1", "Password1", "Password2");
    }

    @Test
    void markRealNameRequiresInternalApiKey() {
        AuthController controller = new AuthController(authService, accounts, "internal-key");

        assertThatThrownBy(() -> controller.markRealName("patient-1", "wrong-key"))
                .isInstanceOf(ResponseStatusException.class);

        controller.markRealName("patient-1", "internal-key");

        verify(accounts).markRealNameVerified("patient-1");
    }
}
