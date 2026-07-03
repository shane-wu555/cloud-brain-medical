package com.cloudbrain.auth.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public class UserAccount implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String id;
    private final String username;
    private final String password;
    private final String phone;
    private final String name;
    private final String role;
    private final List<String> permissions;
    private boolean realNameVerified;
    private final String employeeNo;
    private final boolean active;
    private final LocalDateTime createdAt;

    public UserAccount(
            String id,
            String username,
            String password,
            String phone,
            String name,
            String role,
            List<String> permissions,
            boolean realNameVerified,
            String employeeNo) {
        this(id, username, password, phone, name, role, permissions, realNameVerified, employeeNo, true,
                LocalDateTime.now());
    }

    public UserAccount(
            String id,
            String username,
            String password,
            String phone,
            String name,
            String role,
            List<String> permissions,
            boolean realNameVerified,
            String employeeNo,
            boolean active,
            LocalDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.phone = phone;
        this.name = name;
        this.role = role;
        this.permissions = permissions;
        this.realNameVerified = realNameVerified;
        this.employeeNo = employeeNo;
        this.active = active;
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getPhone() {
        return phone;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public boolean isRealNameVerified() {
        return realNameVerified;
    }

    public String getEmployeeNo() {
        return employeeNo;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void verifyRealName() {
        this.realNameVerified = true;
    }
}

