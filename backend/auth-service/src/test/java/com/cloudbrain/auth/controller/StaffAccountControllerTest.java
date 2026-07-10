package com.cloudbrain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.auth.entity.UserAccount;
import com.cloudbrain.auth.repository.UserAccountRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class StaffAccountControllerTest {
    @Mock
    UserAccountRepository accounts;

    @Mock
    PasswordEncoder passwordEncoder;

    @Test
    void listNormalizesRoleAndFiltersUnmanagedAccounts() {
        StaffAccountController controller = new StaffAccountController(accounts, passwordEncoder);
        UserAccount managed = account("doctor-1", "OUTPATIENT_DOCTOR");
        UserAccount unmanaged = account("admin-1", "ADMIN");
        when(accounts.staffAccounts("OUTPATIENT_DOCTOR")).thenReturn(List.of(managed, unmanaged));

        List<StaffAccountController.StaffAccountDto> result = controller.list(" outpatient_doctor ");

        assertThat(result).extracting(StaffAccountController.StaffAccountDto::id).containsExactly("doctor-1");
    }

    @Test
    void createEncodesPasswordAndTrimsValues() {
        StaffAccountController controller = new StaffAccountController(accounts, passwordEncoder);
        StaffAccountController.CreateStaffAccountRequest request =
                new StaffAccountController.CreateStaffAccountRequest(" 00010001 ", " Doctor ", "CHECK_DOCTOR", " ", "Password1");
        when(accounts.existsByUsername("00010001")).thenReturn(false);
        when(accounts.findByEmployeeNo("00010001")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Password1")).thenReturn("encoded");
        when(accounts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StaffAccountController.StaffAccountDto result = controller.create(request);

        assertThat(result.employeeNo()).isEqualTo("00010001");
        assertThat(result.name()).isEqualTo("Doctor");
        verify(accounts).save(any());
    }

    @Test
    void createRejectsDuplicateRoleAndWeakPassword() {
        StaffAccountController controller = new StaffAccountController(accounts, passwordEncoder);
        when(accounts.existsByUsername("00010001")).thenReturn(true);

        assertThatThrownBy(() -> controller.create(
                new StaffAccountController.CreateStaffAccountRequest("00010001", "Doctor", "OUTPATIENT_DOCTOR", "", "Password1")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> controller.create(
                new StaffAccountController.CreateStaffAccountRequest("00010002", "Doctor", "UNKNOWN", "", "Password1")))
                .isInstanceOf(IllegalArgumentException.class);

        when(accounts.existsByUsername("00010002")).thenReturn(false);
        when(accounts.findByEmployeeNo("00010002")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> controller.create(
                new StaffAccountController.CreateStaffAccountRequest("00010002", "Doctor", "OUTPATIENT_DOCTOR", "", "weak")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(passwordEncoder, never()).encode("weak");
    }

    @Test
    void updateResetAndActivateDelegateToRepository() {
        StaffAccountController controller = new StaffAccountController(accounts, passwordEncoder);
        UserAccount account = account("doctor-1", "PHARMACY_STAFF");
        when(accounts.updateStaffProfile("doctor-1", "Doctor", "13800000000", "PHARMACY_STAFF")).thenReturn(account);
        when(passwordEncoder.encode("Password2")).thenReturn("encoded-2");
        when(accounts.updateStaffPassword("doctor-1", "encoded-2")).thenReturn(account);
        when(accounts.setStaffActive("doctor-1", false)).thenReturn(account);

        StaffAccountController.StaffAccountDto updated = controller.updateProfile(
                "doctor-1",
                new StaffAccountController.UpdateStaffAccountRequest("Doctor", "PHARMACY_STAFF", "13800000000"));
        StaffAccountController.StaffAccountDto reset = controller.resetPassword(
                "doctor-1",
                new StaffAccountController.ResetStaffPasswordRequest("Password2"));
        StaffAccountController.StaffAccountDto active = controller.setActive(
                "doctor-1",
                new StaffAccountController.UpdateStaffActiveRequest(false));

        assertThat(updated.role()).isEqualTo("PHARMACY_STAFF");
        assertThat(reset.id()).isEqualTo("doctor-1");
        assertThat(active.active()).isTrue();
    }

    private UserAccount account(String id, String role) {
        return new UserAccount(
                id,
                id,
                "encoded",
                "13800000000",
                "Doctor",
                role,
                List.of("permission"),
                true,
                id,
                true,
                LocalDateTime.parse("2026-07-10T10:00:00"));
    }
}
