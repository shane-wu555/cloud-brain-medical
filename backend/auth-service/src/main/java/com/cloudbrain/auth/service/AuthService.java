package com.cloudbrain.auth.service;

import com.cloudbrain.auth.controller.AuthController;
import com.cloudbrain.auth.entity.UserAccount;
import com.cloudbrain.auth.repository.UserAccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserAccountRepository repository;

    public AuthService(UserAccountRepository repository) {
        this.repository = repository;
    }

    public Map<String, Object> register(AuthController.RegisterRequest request) {
        if (request.phone() == null || request.phone().isBlank()) {
            throw new IllegalArgumentException("手机号不能为空");
        }
        if (request.password() == null || request.password().length() < 8) {
            throw new IllegalArgumentException("密码至少 8 位");
        }
        if (repository.existsByUsername(request.phone())) {
            throw new IllegalArgumentException("手机号已注册");
        }
        UserAccount account = new UserAccount(
                "patient-" + String.format("%03d", repository.size() + 1),
                request.phone(),
                request.password(),
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
        if (!account.getPassword().equals(request.password())) {
            throw new IllegalArgumentException("密码错误");
        }
        return Map.of(
                "token", "dev-token-" + account.getRole().toLowerCase() + "-" + Instant.now().toEpochMilli(),
                "user", Map.of(
                        "id", account.getId(),
                        "name", account.getName(),
                        "phone", account.getPhone(),
                        "role", account.getRole(),
                        "realNameVerified", account.isRealNameVerified(),
                        "permissions", account.getPermissions()));
    }
}

