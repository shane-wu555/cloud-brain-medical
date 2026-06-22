package com.cloudbrain.auth.service;

import com.cloudbrain.auth.controller.AuthController;
import com.cloudbrain.auth.entity.UserAccount;
import com.cloudbrain.auth.repository.UserAccountRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(UserAccountRepository repository, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    public Map<String, Object> register(AuthController.RegisterRequest request) {
        if (request.phone() == null || request.phone().isBlank()) {
            throw new IllegalArgumentException("手机号不能为空");
        }
        if (request.password() == null
                || request.password().length() != 8
                || !request.password().matches(".*[A-Za-z].*")
                || !request.password().matches(".*\\d.*")) {
            throw new IllegalArgumentException("密码必须为 8 位且同时包含字母和数字");
        }
        if (request.smsCode() == null || request.smsCode().isBlank()) {
            throw new IllegalArgumentException("短信验证码不能为空");
        }
        if (repository.existsByUsername(request.phone())) {
            throw new IllegalArgumentException("手机号已注册");
        }
        UserAccount account = new UserAccount(
                "patient-" + UUID.randomUUID(),
                request.phone(),
                passwordEncoder.encode(request.password()),
                request.phone(),
                request.name(),
                "PATIENT",
                List.of("appointment:create", "appointment:cancel", "medical-record:read"),
                false);
        repository.save(account);
        return login(new AuthController.LoginRequest(request.phone(), request.password()));
    }

    public Map<String, Object> login(AuthController.LoginRequest request) {
        UserAccount account = repository.findByUsername(request.username())
                .orElseThrow(() -> new IllegalArgumentException("账号不存在"));
        if (!passwordEncoder.matches(request.password(), account.getPassword())) {
            throw new IllegalArgumentException("密码错误");
        }
        return Map.of(
                "token", tokenService.issue(account),
                "user", Map.of(
                        "id", account.getId(),
                        "name", account.getName(),
                        "phone", account.getPhone(),
                        "role", account.getRole(),
                        "realNameVerified", account.isRealNameVerified(),
                        "permissions", account.getPermissions()));
    }
}
