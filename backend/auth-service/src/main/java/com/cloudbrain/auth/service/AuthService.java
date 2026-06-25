package com.cloudbrain.auth.service;

import com.cloudbrain.auth.controller.AuthController;
import com.cloudbrain.auth.entity.UserAccount;
import com.cloudbrain.auth.repository.UserAccountRepository;
import com.cloudbrain.auth.repository.AuthAuditRepository;
import com.cloudbrain.auth.repository.VerificationCodeRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuthAuditRepository auditRepository;
    private final VerificationCodeRepository verificationCodes;
    private final long verificationCodeTtlSeconds;
    private final boolean exposeVerificationCode;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserAccountRepository repository, PasswordEncoder passwordEncoder, TokenService tokenService,
            AuthAuditRepository auditRepository,
            VerificationCodeRepository verificationCodes,
            @Value("${security.verification-code.ttl-seconds:300}") long verificationCodeTtlSeconds,
            @Value("${security.verification-code.expose-in-response:false}") boolean exposeVerificationCode) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.auditRepository = auditRepository;
        this.verificationCodes = verificationCodes;
        this.verificationCodeTtlSeconds = verificationCodeTtlSeconds;
        this.exposeVerificationCode = exposeVerificationCode;
    }

    public Map<String, Object> register(AuthController.RegisterRequest request, ClientInfo client) {
        if (request.password() == null
                || request.password().length() < 8
                || request.password().length() > 72
                || !request.password().matches(".*[A-Za-z].*")
                || !request.password().matches(".*\\d.*")) {
            throw new IllegalArgumentException("密码必须为 8-72 位且同时包含字母和数字");
        }
        verifyCode(request.phone(), "REGISTER", request.smsCode());
        if (repository.existsByUsername(request.phone()) || repository.findByPhone(request.phone()).isPresent()) {
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
        auditRepository.record("REGISTER", request.phone(), account.getId(), true, null, client.ip(), client.userAgent());
        return issueLogin(account);
    }

    public Map<String, Object> login(AuthController.LoginRequest request, ClientInfo client) {
        UserAccount account = repository.findByUsername(request.username()).orElse(null);
        boolean passwordMatched = account != null && passwordEncoder.matches(request.password(), account.getPassword());
        if (account == null || !passwordMatched) {
            log.warn("Login rejected: username={}, accountFound={}, passwordMatched={}, passwordLength={}, hashPrefix={}",
                    request.username(),
                    account != null,
                    passwordMatched,
                    request.password() == null ? null : request.password().length(),
                    account == null || account.getPassword() == null
                            ? null
                            : account.getPassword().substring(0, Math.min(12, account.getPassword().length())));
            auditRepository.record("LOGIN", request.username(), account == null ? null : account.getId(), false,
                    "INVALID_CREDENTIALS", client.ip(), client.userAgent());
            throw new IllegalArgumentException("账号或密码错误");
        }
        log.info("Login accepted: username={}, userId={}, role={}",
                request.username(), account.getId(), account.getRole());
        Map<String, Object> result = issueLogin(account);
        auditRepository.record("LOGIN", request.username(), account.getId(), true, null, client.ip(), client.userAgent());
        return result;
    }

    public Map<String, Object> sendCode(AuthController.SendCodeRequest request, ClientInfo client) {
        String purpose = normalizePurpose(request.purpose());
        if ("REGISTER".equals(purpose) && repository.findByPhone(request.phone()).isPresent()) {
            throw new IllegalArgumentException("手机号已注册");
        }
        if (!"REGISTER".equals(purpose) && repository.findByPhone(request.phone()).isEmpty()) {
            throw new IllegalArgumentException("手机号尚未注册");
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        verificationCodes.create(request.phone(), purpose, passwordEncoder.encode(code),
                Instant.now().plusSeconds(verificationCodeTtlSeconds));
        auditRepository.record("SEND_SMS_CODE", request.phone(), null, true, null, client.ip(), client.userAgent());
        java.util.HashMap<String, Object> response = new java.util.HashMap<>();
        response.put("expiresIn", verificationCodeTtlSeconds);
        if (exposeVerificationCode) response.put("devCode", code);
        return response;
    }

    public Map<String, Object> smsLogin(AuthController.SmsLoginRequest request, ClientInfo client) {
        verifyCode(request.phone(), "LOGIN", request.smsCode());
        UserAccount account = repository.findByPhone(request.phone())
                .orElseThrow(() -> new IllegalArgumentException("手机号尚未注册"));
        Map<String, Object> result = issueLogin(account);
        auditRepository.record("SMS_LOGIN", request.phone(), account.getId(), true, null, client.ip(), client.userAgent());
        return result;
    }

    public void resetPassword(AuthController.ResetPasswordRequest request, ClientInfo client) {
        validatePassword(request.newPassword());
        verifyCode(request.phone(), "RESET_PASSWORD", request.smsCode());
        UserAccount account = repository.findByPhone(request.phone())
                .orElseThrow(() -> new IllegalArgumentException("手机号尚未注册"));
        repository.updatePassword(account.getId(), passwordEncoder.encode(request.newPassword()));
        auditRepository.record("RESET_PASSWORD", request.phone(), account.getId(), true, null, client.ip(), client.userAgent());
    }

    private void verifyCode(String phone, String purpose, String code) {
        VerificationCodeRepository.VerificationCode saved = verificationCodes.latestActive(phone, purpose)
                .orElseThrow(() -> new IllegalArgumentException("验证码无效或已过期"));
        if (!passwordEncoder.matches(code, saved.codeHash()) || !verificationCodes.consume(saved.id())) {
            throw new IllegalArgumentException("验证码无效或已过期");
        }
    }

    private String normalizePurpose(String purpose) {
        String normalized = purpose == null ? "" : purpose.trim().toUpperCase();
        if (!List.of("REGISTER", "LOGIN", "RESET_PASSWORD").contains(normalized)) {
            throw new IllegalArgumentException("不支持的验证码用途");
        }
        return normalized;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 72
                || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new IllegalArgumentException("密码必须为 8-72 位且同时包含字母和数字");
        }
    }

    private Map<String, Object> issueLogin(UserAccount account) {
        return Map.of(
                "token", tokenService.issue(account),
                "user", Map.of(
                        "id", account.getId(),
                        "username", account.getUsername(),
                        "name", account.getName(),
                        "phone", account.getPhone(),
                        "role", account.getRole(),
                        "realNameVerified", account.isRealNameVerified(),
                        "permissions", account.getPermissions()));
    }

    public record ClientInfo(String ip, String userAgent) {
    }
}
