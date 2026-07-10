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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
        when(verificationCodes.latestActive("13800000000", "REGISTER")).thenReturn(Optional.of(
                new VerificationCodeRepository.VerificationCode(
                        UUID.randomUUID(),
                        new BCryptPasswordEncoder().encode("123456"),
                        Instant.now().plusSeconds(300))));
        when(verificationCodes.consume(any())).thenReturn(true);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenService.issue(any())).thenReturn("signed-token");

        Map<String, Object> result = service.register(
                new AuthController.RegisterRequest("13800000000", "abc12345", "测试患者", "123456"),
                new AuthService.ClientInfo("127.0.0.1", "test"));

        assertThat(result.get("token")).isEqualTo("signed-token");
        verify(repository).save(org.mockito.ArgumentMatchers.argThat(account ->
                !account.getPassword().equals("abc12345")
                        && new BCryptPasswordEncoder().matches("abc12345", account.getPassword())));
        verify(auditRepository).record(
                eq("REGISTER"),
                eq("13800000000"),
                eq("测试患者"),
                any(),
                eq("PATIENT"),
                eq(true),
                eq(null),
                eq("127.0.0.1"),
                eq("test"));
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
        verify(auditRepository).record(
                "LOGIN",
                "00010001",
                "张医生",
                "00010001",
                "OUTPATIENT_DOCTOR",
                false,
                "INVALID_CREDENTIALS",
                "10.0.0.2",
                "test");
    }

    @Test
    void successfulLoginIssuesTokenAndAuditsSuccess() {
        UserAccount account = account(new BCryptPasswordEncoder().encode("Password1"));
        when(repository.findByEmployeeNo("00010001")).thenReturn(Optional.of(account));
        when(tokenService.issue(account)).thenReturn("token-success");

        Map<String, Object> result = service.login(
                new AuthController.LoginRequest("00010001", "Password1"),
                new AuthService.ClientInfo("10.0.0.3", "browser"));

        assertThat(result).containsEntry("token", "token-success");
        verify(auditRepository).record(
                "LOGIN",
                "00010001",
                account.getName(),
                account.getId(),
                account.getRole(),
                true,
                null,
                "10.0.0.3",
                "browser");
    }

    @Test
    void loginRejectsDisabledAccount() {
        UserAccount disabled = new UserAccount(
                "00010002",
                "00010002",
                new BCryptPasswordEncoder().encode("Password1"),
                "13700000102",
                "Doctor Disabled",
                "OUTPATIENT_DOCTOR",
                List.of("medical-record:write"),
                true,
                "00010002",
                false,
                Instant.parse("2026-07-10T00:00:00Z").atZone(java.time.ZoneOffset.UTC).toLocalDateTime());
        when(repository.findByEmployeeNo("00010002")).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> service.login(
                new AuthController.LoginRequest("00010002", "Password1"),
                new AuthService.ClientInfo("10.0.0.4", "browser")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registerRejectsExistingPhoneAndInvalidPassword() {
        when(verificationCodes.latestActive("13800000001", "REGISTER")).thenReturn(Optional.of(
                new VerificationCodeRepository.VerificationCode(
                        UUID.randomUUID(),
                        new BCryptPasswordEncoder().encode("123456"),
                        Instant.now().plusSeconds(300))));
        when(verificationCodes.consume(any())).thenReturn(true);
        when(repository.existsByUsername("13800000001")).thenReturn(true);

        assertThatThrownBy(() -> service.register(
                new AuthController.RegisterRequest("13800000001", "Password1", "Alice", "123456"),
                new AuthService.ClientInfo("127.0.0.1", "test")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.register(
                new AuthController.RegisterRequest("13800000002", "short", "Alice", "123456"),
                new AuthService.ClientInfo("127.0.0.1", "test")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registerRejectsPhoneAlreadyBoundToAnotherAccount() {
        when(verificationCodes.latestActive("13800000009", "REGISTER")).thenReturn(Optional.of(
                new VerificationCodeRepository.VerificationCode(
                        UUID.randomUUID(),
                        new BCryptPasswordEncoder().encode("123456"),
                        Instant.now().plusSeconds(300))));
        when(verificationCodes.consume(any())).thenReturn(true);
        when(repository.existsByUsername("13800000009")).thenReturn(false);
        when(repository.findByPhone("13800000009")).thenReturn(Optional.of(account("encoded")));

        assertThatThrownBy(() -> service.register(
                new AuthController.RegisterRequest("13800000009", "Password1", "Alice", "123456"),
                new AuthService.ClientInfo("127.0.0.1", "test")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sendCodeRejectsUnsupportedPurposeAndMissingAccount() {
        assertThatThrownBy(() -> service.sendCode(
                new AuthController.SendCodeRequest("13800000003", "unknown"),
                new AuthService.ClientInfo("127.0.0.1", "test")))
                .isInstanceOf(IllegalArgumentException.class);

        when(repository.findByPhone("13800000004")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendCode(
                new AuthController.SendCodeRequest("13800000004", "RESET_PASSWORD"),
                new AuthService.ClientInfo("127.0.0.1", "test")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sendCodeRejectsRegisterForExistingPhoneAndDoesNotExposeCodeWhenFeatureDisabled() {
        when(repository.findByPhone("13800000010")).thenReturn(Optional.of(account("encoded")));

        assertThatThrownBy(() -> service.sendCode(
                new AuthController.SendCodeRequest("13800000010", "register"),
                new AuthService.ClientInfo("127.0.0.1", "test")))
                .isInstanceOf(IllegalArgumentException.class);

        AuthService hiddenCodeService = new AuthService(
                repository,
                new BCryptPasswordEncoder(),
                tokenService,
                auditRepository,
                verificationCodes,
                smsSender,
                300,
                false);
        when(repository.findByPhone("13800000011")).thenReturn(Optional.of(account("encoded")));

        Map<String, Object> result = hiddenCodeService.sendCode(
                new AuthController.SendCodeRequest("13800000011", " login "),
                new AuthService.ClientInfo("127.0.0.1", "test"));

        assertThat(result).doesNotContainKey("devCode").containsEntry("expiresIn", 300L);
    }

    @Test
    void smsLoginAndResetPasswordUseVerificationCodeAndRepository() {
        UserAccount account = account("$2a$10$7NukEsugMLsxrPkaBLnhuOHHhSQg2RjHt4RiGYxJNx7pq9cyG6bL.");
        when(verificationCodes.latestActive("13800000000", "LOGIN")).thenReturn(Optional.of(
                new VerificationCodeRepository.VerificationCode(
                        UUID.randomUUID(),
                        new BCryptPasswordEncoder().encode("654321"),
                        Instant.now().plusSeconds(300))));
        when(verificationCodes.latestActive("13800000000", "RESET_PASSWORD")).thenReturn(Optional.of(
                new VerificationCodeRepository.VerificationCode(
                        UUID.randomUUID(),
                        new BCryptPasswordEncoder().encode("111111"),
                        Instant.now().plusSeconds(300))));
        when(verificationCodes.consume(any())).thenReturn(true);
        when(repository.findByPhone("13800000000")).thenReturn(Optional.of(account));
        when(tokenService.issue(account)).thenReturn("sms-token");

        Map<String, Object> smsLogin = service.smsLogin(
                new AuthController.SmsLoginRequest("13800000000", "654321"),
                new AuthService.ClientInfo("10.0.0.5", "client"));
        service.resetPassword(
                new AuthController.ResetPasswordRequest("13800000000", "111111", "Password2"),
                new AuthService.ClientInfo("10.0.0.6", "client"));

        assertThat(smsLogin).containsEntry("token", "sms-token");
        verify(repository).updatePassword(eq(account.getId()), any());
    }

    @Test
    void smsLoginAndResetPasswordRejectWhenAccountDoesNotExist() {
        when(verificationCodes.latestActive("13800000012", "LOGIN")).thenReturn(Optional.of(
                new VerificationCodeRepository.VerificationCode(
                        UUID.randomUUID(),
                        new BCryptPasswordEncoder().encode("654321"),
                        Instant.now().plusSeconds(300))));
        when(verificationCodes.latestActive("13800000012", "RESET_PASSWORD")).thenReturn(Optional.of(
                new VerificationCodeRepository.VerificationCode(
                        UUID.randomUUID(),
                        new BCryptPasswordEncoder().encode("111111"),
                        Instant.now().plusSeconds(300))));
        when(verificationCodes.consume(any())).thenReturn(true);
        when(repository.findByPhone("13800000012")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.smsLogin(
                new AuthController.SmsLoginRequest("13800000012", "654321"),
                new AuthService.ClientInfo("10.0.0.5", "client"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.resetPassword(
                new AuthController.ResetPasswordRequest("13800000012", "111111", "Password2"),
                new AuthService.ClientInfo("10.0.0.6", "client"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verifyCodeRejectsMissingOrConsumedCode() {
        when(verificationCodes.latestActive("13800000013", "LOGIN")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.smsLogin(
                new AuthController.SmsLoginRequest("13800000013", "654321"),
                new AuthService.ClientInfo("10.0.0.7", "client"))).isInstanceOf(IllegalArgumentException.class);

        when(verificationCodes.latestActive("13800000014", "LOGIN")).thenReturn(Optional.of(
                new VerificationCodeRepository.VerificationCode(
                        UUID.randomUUID(),
                        new BCryptPasswordEncoder().encode("654321"),
                        Instant.now().plusSeconds(300))));
        when(verificationCodes.consume(any())).thenReturn(false);
        assertThatThrownBy(() -> service.smsLogin(
                new AuthController.SmsLoginRequest("13800000014", "654321"),
                new AuthService.ClientInfo("10.0.0.8", "client"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changePasswordRejectsWrongOldPasswordAndUpdatesWhenValid() {
        UserAccount account = account(new BCryptPasswordEncoder().encode("Password1"));
        when(repository.findById("00010001")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.changePassword("00010001", "wrong", "Password2"))
                .isInstanceOf(IllegalArgumentException.class);

        service.changePassword("00010001", "Password1", "Password2");

        verify(repository).updatePassword(eq("00010001"), any());
    }

    @Test
    void changePasswordRejectsMissingAccountAndWeakNewPassword() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.changePassword("missing", "Password1", "Password2"))
                .isInstanceOf(IllegalArgumentException.class);

        UserAccount account = account(new BCryptPasswordEncoder().encode("Password1"));
        when(repository.findById("00010003")).thenReturn(Optional.of(account));
        assertThatThrownBy(() -> service.changePassword("00010003", "Password1", "weak"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loginForPatientAccountOmitsBlankOptionalFieldsFromUserPayload() {
        UserAccount patient = new UserAccount(
                "patient-1",
                "13800009999",
                new BCryptPasswordEncoder().encode("Password1"),
                null,
                "Patient",
                "PATIENT",
                List.of("appointment:create"),
                false,
                "   ");
        when(repository.findByEmployeeNo("13800009999")).thenReturn(Optional.empty());
        when(repository.findByUsername("13800009999")).thenReturn(Optional.of(patient));
        when(tokenService.issue(patient)).thenReturn("patient-token");

        Map<String, Object> result = service.login(
                new AuthController.LoginRequest("13800009999", "Password1"),
                new AuthService.ClientInfo("10.0.0.9", "browser"));

        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) result.get("user");
        assertThat(user).doesNotContainKeys("employeeNo", "phone");
    }

    private UserAccount account(String encodedPassword) {
        return new UserAccount(
                "00010001",
                "00010001",
                encodedPassword,
                "13700000101",
                "张医生",
                "OUTPATIENT_DOCTOR",
                List.of("medical-record:write"),
                true,
                "00010001");
    }
}
