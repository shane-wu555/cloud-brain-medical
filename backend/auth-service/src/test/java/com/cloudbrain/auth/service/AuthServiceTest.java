package com.cloudbrain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.auth.controller.AuthController;
import com.cloudbrain.auth.entity.UserAccount;
import com.cloudbrain.auth.repository.AuthAuditRepository;
import com.cloudbrain.auth.repository.UserAccountRepository;
import com.cloudbrain.auth.repository.VerificationCodeRepository;
import com.cloudbrain.auth.sms.SmsSender;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock private UserAccountRepository repository;
    @Mock private TokenService tokenService;
    @Mock private AuthAuditRepository auditRepository;
    @Mock private VerificationCodeRepository verificationCodes;
    @Mock private SmsSender smsSender;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(repository, new BCryptPasswordEncoder(), tokenService, auditRepository,
                verificationCodes, smsSender, 300, true);
    }

    @Test
    void registerHashesPasswordAndAuditsRegistration() {
        when(repository.existsByUsername("13800000000")).thenReturn(false);
        when(verificationCodes.latestActive("13800000000", "REGISTER")).thenReturn(java.util.Optional.of(
                new VerificationCodeRepository.VerificationCode(java.util.UUID.randomUUID(),
                        new BCryptPasswordEncoder().encode("123456"), java.time.Instant.now().plusSeconds(300))));
        when(verificationCodes.consume(any())).thenReturn(true);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenService.issue(any())).thenReturn("signed-token");

        Map<String, Object> result = service.register(
                new AuthController.RegisterRequest("13800000000", "abc12345", "患者", "123456"),
                new AuthService.ClientInfo("127.0.0.1", "test"));

        assertThat(result.get("token")).isEqualTo("signed-token");
        verify(repository).save(org.mockito.ArgumentMatchers.argThat(account ->
                !account.getPassword().equals("abc12345")
                        && new BCryptPasswordEncoder().matches("abc12345", account.getPassword())));
        verify(auditRepository).record(eq("REGISTER"), eq("13800000000"), any(), eq(true),
                eq(null), eq("127.0.0.1"), eq("test"));
    }

    @Test
    void sendCodeExposesDevCodeOnlyWhenSmsIsMocked() {
        when(repository.findByPhone("13800000000")).thenReturn(Optional.of(account("$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.")));
        when(smsSender.isLive()).thenReturn(false);

        Map<String, Object> result = service.sendCode(
                new AuthController.SendCodeRequest("13800000000", "LOGIN"),
                new AuthService.ClientInfo("127.0.0.1", "test"));

        assertThat(result).containsKey("devCode");
        verify(smsSender).sendVerificationCode(eq("13800000000"), eq("LOGIN"), any());
    }

    @Test
    void sendCodeDoesNotExposeDevCodeWhenSmsIsLive() {
        when(repository.findByPhone("13800000000")).thenReturn(Optional.of(account("$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.")));
        when(smsSender.isLive()).thenReturn(true);

        Map<String, Object> result = service.sendCode(
                new AuthController.SendCodeRequest("13800000000", "LOGIN"),
                new AuthService.ClientInfo("127.0.0.1", "test"));

        assertThat(result).doesNotContainKey("devCode");
    }

    @Test
    void failedLoginDoesNotIssueTokenAndIsAudited() {
        UserAccount account = account("$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.");
        when(repository.findByUsername("00010001")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.login(
                new AuthController.LoginRequest("00010001", "wrong-password"),
                new AuthService.ClientInfo("10.0.0.2", "test")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("账号或密码错误");

        verify(tokenService, never()).issue(any());
        verify(auditRepository).record("LOGIN", "00010001", "00010001", false,
                "INVALID_CREDENTIALS", "10.0.0.2", "test");
    }

    private UserAccount account(String encodedPassword) {
        // 新设计：员工 id = username = employee_no（工号）
        return new UserAccount("00010001", "00010001", encodedPassword, "13700000101", "张医生",
                "OUTPATIENT_DOCTOR", List.of("medical-record:write"), true, "00010001");
    }
}
