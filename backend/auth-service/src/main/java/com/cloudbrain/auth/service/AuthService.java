package com.cloudbrain.auth.service;

import com.cloudbrain.auth.controller.AuthController;
import com.cloudbrain.auth.entity.UserAccount;
import com.cloudbrain.auth.repository.AuthAuditRepository;
import com.cloudbrain.auth.repository.UserAccountRepository;
import com.cloudbrain.auth.repository.VerificationCodeRepository;
import com.cloudbrain.auth.sms.SmsSender;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuthAuditRepository auditRepository;
    private final VerificationCodeRepository verificationCodes;
    private final SmsSender smsSender;
    private final long verificationCodeTtlSeconds;
    private final boolean exposeVerificationCode;
    private final SecureRandom random = new SecureRandom();

    public AuthService(
            UserAccountRepository repository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            AuthAuditRepository auditRepository,
            VerificationCodeRepository verificationCodes,
            SmsSender smsSender,
            @Value("${security.verification-code.ttl-seconds:300}") long verificationCodeTtlSeconds,
            @Value("${security.verification-code.expose-in-response:false}") boolean exposeVerificationCode) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.auditRepository = auditRepository;
        this.verificationCodes = verificationCodes;
        this.smsSender = smsSender;
        this.verificationCodeTtlSeconds = verificationCodeTtlSeconds;
        this.exposeVerificationCode = exposeVerificationCode;
    }

    public Map<String, Object> register(AuthController.RegisterRequest request, ClientInfo client) {
        validatePassword(request.password());
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
                false,
                null);
        repository.save(account);
        auditRepository.record(
                "REGISTER",
                request.phone(),
                request.name(),
                account.getId(),
                account.getRole(),
                true,
                null,
                client.ip(),
                client.userAgent());
        return issueLogin(account);
    }

    public Map<String, Object> login(AuthController.LoginRequest request, ClientInfo client) {
        UserAccount account = repository.findByEmployeeNo(request.username())
                .or(() -> repository.findByUsername(request.username()))
                .orElse(null);
        boolean passwordMatched = account != null && passwordEncoder.matches(request.password(), account.getPassword());
        if (account == null || !passwordMatched) {
            log.warn(
                    "Login rejected: username={}, accountFound={}, passwordMatched={}, passwordLength={}, hashPrefix={}",
                    request.username(),
                    account != null,
                    passwordMatched,
                    request.password() == null ? null : request.password().length(),
                    account == null || account.getPassword() == null
                            ? null
                            : account.getPassword().substring(0, Math.min(12, account.getPassword().length())));
            auditRepository.record(
                    "LOGIN",
                    request.username(),
                    account == null ? null : account.getName(),
                    account == null ? null : account.getId(),
                    account == null ? null : account.getRole(),
                    false,
                    "INVALID_CREDENTIALS",
                    client.ip(),
                    client.userAgent());
            throw new IllegalArgumentException("账号或密码错误");
        }
        if (!account.isActive()) {
            auditRepository.record(
                    "LOGIN",
                    request.username(),
                    account.getName(),
                    account.getId(),
                    account.getRole(),
                    false,
                    "ACCOUNT_DISABLED",
                    client.ip(),
                    client.userAgent());
            throw new IllegalArgumentException("账号已停用，请联系管理员");
        }

        log.info("Login accepted: username={}, userId={}, role={}",
                request.username(), account.getId(), account.getRole());
        Map<String, Object> result = issueLogin(account);
        auditRepository.record(
                "LOGIN",
                request.username(),
                account.getName(),
                account.getId(),
                account.getRole(),
                true,
                null,
                client.ip(),
                client.userAgent());
        return result;
    }

    public Map<String, Object> sendCode(AuthController.SendCodeRequest request, ClientInfo client) {
        String purpose = normalizePurpose(request.purpose());
        Optional<UserAccount> existingAccount = repository.findByPhone(request.phone());
        if ("REGISTER".equals(purpose) && existingAccount.isPresent()) {
            throw new IllegalArgumentException("手机号已注册");
        }
        if (!"REGISTER".equals(purpose) && existingAccount.isEmpty()) {
            throw new IllegalArgumentException("手机号尚未注册");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        verificationCodes.create(
                request.phone(),
                purpose,
                passwordEncoder.encode(code),
                Instant.now().plusSeconds(verificationCodeTtlSeconds));
        smsSender.sendVerificationCode(request.phone(), purpose, code);
        auditRepository.record(
                "SEND_SMS_CODE",
                request.phone(),
                existingAccount.map(UserAccount::getName).orElse(null),
                existingAccount.map(UserAccount::getId).orElse(null),
                existingAccount.map(UserAccount::getRole).orElse(null),
                true,
                null,
                client.ip(),
                client.userAgent(),
                Map.of("purpose", purpose));

        Map<String, Object> response = new HashMap<>();
        response.put("expiresIn", verificationCodeTtlSeconds);
        if (exposeVerificationCode && !smsSender.isLive()) {
            response.put("devCode", code);
        }
        return response;
    }

    public Map<String, Object> smsLogin(AuthController.SmsLoginRequest request, ClientInfo client) {
        verifyCode(request.phone(), "LOGIN", request.smsCode());
        UserAccount account = repository.findByPhone(request.phone())
                .orElseThrow(() -> new IllegalArgumentException("手机号尚未注册"));
        Map<String, Object> result = issueLogin(account);
        auditRepository.record(
                "SMS_LOGIN",
                request.phone(),
                account.getName(),
                account.getId(),
                account.getRole(),
                true,
                null,
                client.ip(),
                client.userAgent());
        return result;
    }

    public void resetPassword(AuthController.ResetPasswordRequest request, ClientInfo client) {
        validatePassword(request.newPassword());
        verifyCode(request.phone(), "RESET_PASSWORD", request.smsCode());
        UserAccount account = repository.findByPhone(request.phone())
                .orElseThrow(() -> new IllegalArgumentException("手机号尚未注册"));
        repository.updatePassword(account.getId(), passwordEncoder.encode(request.newPassword()));
        auditRepository.record(
                "RESET_PASSWORD",
                request.phone(),
                account.getName(),
                account.getId(),
                account.getRole(),
                true,
                null,
                client.ip(),
                client.userAgent());
    }

    public void changePassword(String userId, String oldPassword, String newPassword) {
        UserAccount account = repository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("账号不存在"));
        if (!passwordEncoder.matches(oldPassword, account.getPassword())) {
            throw new IllegalArgumentException("原密码不正确");
        }
        validatePassword(newPassword);
        repository.updatePassword(userId, passwordEncoder.encode(newPassword));
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
            throw new IllegalArgumentException("密码必须为 8-72 位，且同时包含字母和数字");
        }
    }

    private Map<String, Object> issueLogin(UserAccount account) {
        Map<String, Object> user = new HashMap<>();
        user.put("id", account.getId());
        user.put("username", account.getUsername());
        if (account.getEmployeeNo() != null && !account.getEmployeeNo().isBlank()) {
            user.put("employeeNo", account.getEmployeeNo());
        }
        user.put("name", account.getName());
        user.put("role", account.getRole());
        user.put("realNameVerified", account.isRealNameVerified());
        user.put("permissions", account.getPermissions());
        if (account.getPhone() != null && !account.getPhone().isBlank()) {
            user.put("phone", account.getPhone());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("token", tokenService.issue(account));
        result.put("user", user);
        return result;
    }

    public record ClientInfo(String ip, String userAgent) {
    }
}
