package com.cloudbrain.auth.controller;

import com.cloudbrain.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final com.cloudbrain.auth.repository.UserAccountRepository accounts;
    private final String internalApiKey;

    public AuthController(AuthService authService, com.cloudbrain.auth.repository.UserAccountRepository accounts,
            @Value("${internal.api-key}") String internalApiKey) {
        this.authService = authService;
        this.accounts = accounts;
        this.internalApiKey = internalApiKey;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        return authService.register(request, client(servletRequest));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return authService.login(request, client(servletRequest));
    }

    @PostMapping("/sms-codes")
    public Map<String, Object> sendCode(@Valid @RequestBody SendCodeRequest request, HttpServletRequest servletRequest) {
        return authService.sendCode(request, client(servletRequest));
    }

    @PostMapping("/sms-login")
    public Map<String, Object> smsLogin(@Valid @RequestBody SmsLoginRequest request, HttpServletRequest servletRequest) {
        return authService.smsLogin(request, client(servletRequest));
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest servletRequest) {
        authService.resetPassword(request, client(servletRequest));
    }

    @PutMapping("/internal/users/{id}/real-name")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRealName(@PathVariable String id,
            @RequestHeader(name="X-Internal-Api-Key", required=false) String apiKey) {
        if (!internalApiKey.equals(apiKey)) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED);
        accounts.markRealNameVerified(id);
    }

    private AuthService.ClientInfo client(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",", 2)[0].trim();
        return new AuthService.ClientInfo(ip, request.getHeader("User-Agent"));
    }

    public record RegisterRequest(
            @NotBlank @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确") String phone,
            @NotBlank @Size(min = 8, max = 72, message = "密码长度必须为 8-72 位") String password,
            @NotBlank @Size(max = 64) String name,
            @NotBlank String smsCode) {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record SendCodeRequest(
            @NotBlank @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确") String phone,
            @NotBlank String purpose) {}

    public record SmsLoginRequest(
            @NotBlank @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确") String phone,
            @NotBlank String smsCode) {}

    public record ResetPasswordRequest(
            @NotBlank @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确") String phone,
            @NotBlank String smsCode,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {}
}
