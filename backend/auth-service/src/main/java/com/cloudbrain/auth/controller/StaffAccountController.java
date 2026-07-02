package com.cloudbrain.auth.controller;

import com.cloudbrain.auth.entity.UserAccount;
import com.cloudbrain.auth.repository.UserAccountRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/staff-accounts")
@PreAuthorize("hasRole('ADMIN')")
public class StaffAccountController {
    private static final Set<String> MANAGED_ROLES = Set.of(
            "OUTPATIENT_DOCTOR", "CHECK_DOCTOR", "LAB_DOCTOR", "DISPOSAL_DOCTOR", "PHARMACY_STAFF");

    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;

    public StaffAccountController(UserAccountRepository accounts, PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public List<StaffAccountDto> list(@RequestParam(name = "role", required = false) String role) {
        String normalizedRole = normalizeNullableRole(role);
        return accounts.staffAccounts(normalizedRole).stream()
                .filter(account -> MANAGED_ROLES.contains(account.getRole()))
                .map(this::dto)
                .toList();
    }

    @PostMapping
    public StaffAccountDto create(@Valid @RequestBody CreateStaffAccountRequest request) {
        String employeeNo = request.employeeNo().trim();
        if (accounts.existsByUsername(employeeNo) || accounts.findByEmployeeNo(employeeNo).isPresent()) {
            throw new IllegalArgumentException("工号账号已存在");
        }
        String role = normalizeRole(request.role());
        validatePassword(request.password());
        UserAccount account = new UserAccount(
                employeeNo,
                employeeNo,
                passwordEncoder.encode(request.password()),
                blankToNull(request.phone()),
                request.name().trim(),
                role,
                permissionsForRole(role),
                true,
                employeeNo);
        return dto(accounts.save(account));
    }

    @PutMapping("/{id}")
    public StaffAccountDto updateProfile(@PathVariable("id") String id,
            @Valid @RequestBody UpdateStaffAccountRequest request) {
        String role = normalizeRole(request.role());
        return dto(accounts.updateStaffProfile(id, request.name().trim(), blankToNull(request.phone()), role));
    }

    @PutMapping("/{id}/password")
    public StaffAccountDto resetPassword(@PathVariable("id") String id,
            @Valid @RequestBody ResetStaffPasswordRequest request) {
        validatePassword(request.newPassword());
        return dto(accounts.updateStaffPassword(id, passwordEncoder.encode(request.newPassword())));
    }

    @PutMapping("/{id}/active")
    public StaffAccountDto setActive(@PathVariable("id") String id,
            @Valid @RequestBody UpdateStaffActiveRequest request) {
        return dto(accounts.setStaffActive(id, request.active()));
    }

    private StaffAccountDto dto(UserAccount account) {
        return new StaffAccountDto(
                account.getId(),
                account.getUsername(),
                account.getEmployeeNo(),
                account.getName(),
                account.getRole(),
                account.getPhone(),
                account.isActive(),
                account.getCreatedAt());
    }

    private String normalizeRole(String role) {
        String normalized = role == null ? "OUTPATIENT_DOCTOR" : role.trim().toUpperCase();
        if ("PHARMACY_DOCTOR".equals(normalized)) {
            normalized = "PHARMACY_STAFF";
        }
        if (!MANAGED_ROLES.contains(normalized)) {
            throw new IllegalArgumentException("不支持管理该员工角色");
        }
        return normalized;
    }

    private String normalizeNullableRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        return normalizeRole(role);
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 72
                || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new IllegalArgumentException("密码必须为 8-72 位且同时包含字母和数字");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private List<String> permissionsForRole(String role) {
        return switch (role) {
            case "OUTPATIENT_DOCTOR" -> List.of("appointment:read", "medical-record:write", "medical-order:create");
            case "CHECK_DOCTOR", "LAB_DOCTOR", "DISPOSAL_DOCTOR" -> List.of("medical-order:read", "report:write");
            case "PHARMACY_STAFF" -> List.of("prescription:dispense");
            default -> List.of();
        };
    }

    public record StaffAccountDto(
            String id,
            String username,
            String employeeNo,
            String name,
            String role,
            String phone,
            boolean active,
            LocalDateTime createdAt) {
    }

    public record CreateStaffAccountRequest(
            @NotBlank @Size(max = 64) String employeeNo,
            @NotBlank @Size(max = 64) String name,
            @NotBlank String role,
            @Size(max = 16) @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确") String phone,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record UpdateStaffAccountRequest(
            @NotBlank @Size(max = 64) String name,
            @NotBlank String role,
            @Size(max = 16) @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确") String phone) {
    }

    public record ResetStaffPasswordRequest(@NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    public record UpdateStaffActiveRequest(boolean active) {
    }
}
