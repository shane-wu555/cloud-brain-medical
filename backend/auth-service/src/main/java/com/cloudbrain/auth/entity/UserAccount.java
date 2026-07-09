package com.cloudbrain.auth.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public class UserAccount implements Serializable {
    private static final long serialVersionUID = 1L;
    private String id;
    private String username;
    private String password;
    private String phone;
    private String name;
    private String role;
    private List<String> permissions;
    private boolean realNameVerified;
    private String employeeNo;
    private boolean active;
    private LocalDateTime createdAt;

    public UserAccount() {
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
            String employeeNo) {
        this(id, username, password, phone, name, role, permissions, realNameVerified, employeeNo, true,
                LocalDateTime.now());
    }

    @JsonCreator
    public UserAccount(
            @JsonProperty("id") String id,
            @JsonProperty("username") String username,
            @JsonProperty("password") String password,
            @JsonProperty("phone") String phone,
            @JsonProperty("name") String name,
            @JsonProperty("role") String role,
            @JsonProperty("permissions") List<String> permissions,
            @JsonProperty("realNameVerified") boolean realNameVerified,
            @JsonProperty("employeeNo") String employeeNo,
            @JsonProperty("active") boolean active,
            @JsonProperty("createdAt") LocalDateTime createdAt) {
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

