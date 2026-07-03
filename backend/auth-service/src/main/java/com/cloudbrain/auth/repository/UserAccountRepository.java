package com.cloudbrain.auth.repository;

import com.cloudbrain.auth.cache.AuthAccountCacheService;
import com.cloudbrain.auth.entity.UserAccount;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserAccountRepository {
    private final JdbcTemplate jdbcTemplate;
    private final AuthAccountCacheService accountCache;
    private final RowMapper<UserAccount> rowMapper = new UserAccountRowMapper();

    public UserAccountRepository(JdbcTemplate jdbcTemplate, AuthAccountCacheService accountCache) {
        this.jdbcTemplate = jdbcTemplate;
        this.accountCache = accountCache;
    }

    public Optional<UserAccount> findByUsername(String username) {
        return accountCache.findByUsername(username, () -> queryByUsername(username));
    }

    private Optional<UserAccount> queryByUsername(String username) {
        List<UserAccount> result = jdbcTemplate.query(
                "select * from user_account where username = ?",
                rowMapper,
                username);
        return result.stream().findFirst();
    }

    public Optional<UserAccount> findByEmployeeNo(String employeeNo) {
        return accountCache.findByEmployeeNo(employeeNo, () -> queryByEmployeeNo(employeeNo));
    }

    private Optional<UserAccount> queryByEmployeeNo(String employeeNo) {
        List<UserAccount> result = jdbcTemplate.query(
                "select * from user_account where employee_no = ? or username = ?",
                rowMapper,
                employeeNo,
                employeeNo);
        return result.stream().findFirst();
    }

    public Optional<UserAccount> findByPhone(String phone) {
        return accountCache.findByPhone(phone, () -> queryByPhone(phone));
    }

    private Optional<UserAccount> queryByPhone(String phone) {
        List<UserAccount> result = jdbcTemplate.query(
                "select * from user_account where phone = ? order by created_at limit 1", rowMapper, phone);
        return result.stream().findFirst();
    }

    public Optional<UserAccount> findById(String id) {
        return accountCache.findById(id, () -> queryById(id));
    }

    private Optional<UserAccount> queryById(String id) {
        List<UserAccount> result = jdbcTemplate.query("select * from user_account where id = ?", rowMapper, id);
        return result.stream().findFirst();
    }

    public List<UserAccount> staffAccounts(String role) {
        StringBuilder sql = new StringBuilder("""
                select *
                from user_account
                where role <> 'PATIENT'
                """);
        List<Object> args = new java.util.ArrayList<>();
        if (role != null && !role.isBlank()) {
            sql.append(" and role = ?");
            args.add(role);
        }
        sql.append(" order by active desc, created_at desc, username");
        return jdbcTemplate.query(sql.toString(), rowMapper, args.toArray());
    }

    public boolean existsByUsername(String username) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from user_account where username = ?",
                Integer.class,
                username);
        return count != null && count > 0;
    }

    public UserAccount save(UserAccount account) {
        queryByUsername(account.getUsername()).ifPresent(accountCache::evict);
        jdbcTemplate.update("""
                insert into user_account (id, username, password, phone, name, role, permissions, real_name_verified, employee_no, active)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (username) do update set
                    password = excluded.password,
                    phone = excluded.phone,
                    name = excluded.name,
                    role = excluded.role,
                    permissions = excluded.permissions,
                    real_name_verified = excluded.real_name_verified,
                    employee_no = excluded.employee_no,
                    active = excluded.active
                """,
                account.getId(),
                account.getUsername(),
                account.getPassword(),
                account.getPhone(),
                account.getName(),
                account.getRole(),
                String.join(",", account.getPermissions()),
                account.isRealNameVerified(),
                account.getEmployeeNo(),
                account.isActive());
        accountCache.put(account);
        return account;
    }

    public UserAccount updateStaffProfile(String id, String name, String phone, String role) {
        queryById(id).ifPresent(accountCache::evict);
        if (jdbcTemplate.update("""
                update user_account
                set name = ?, phone = ?, role = ?
                where id = ? and role <> 'PATIENT'
                """, name, phone, role, id) != 1) {
            throw new IllegalArgumentException("员工账号不存在");
        }
        UserAccount account = queryById(id).orElseThrow();
        accountCache.put(account);
        return account;
    }

    public void updatePassword(String id, String encodedPassword) {
        queryById(id).ifPresent(accountCache::evict);
        jdbcTemplate.update("update user_account set password = ? where id = ?", encodedPassword, id);
        queryById(id).ifPresent(accountCache::put);
    }

    public UserAccount updateStaffPassword(String id, String encodedPassword) {
        queryById(id).ifPresent(accountCache::evict);
        if (jdbcTemplate.update("update user_account set password = ? where id = ? and role <> 'PATIENT'",
                encodedPassword, id) != 1) {
            throw new IllegalArgumentException("员工账号不存在");
        }
        UserAccount account = queryById(id).orElseThrow();
        accountCache.put(account);
        return account;
    }

    public UserAccount setStaffActive(String id, boolean active) {
        queryById(id).ifPresent(accountCache::evict);
        if (jdbcTemplate.update("update user_account set active = ? where id = ? and role <> 'PATIENT'",
                active, id) != 1) {
            throw new IllegalArgumentException("员工账号不存在");
        }
        UserAccount account = queryById(id).orElseThrow();
        accountCache.put(account);
        return account;
    }

    public void markRealNameVerified(String id) {
        queryById(id).ifPresent(accountCache::evict);
        if (jdbcTemplate.update("update user_account set real_name_verified = true where id = ? and role = 'PATIENT'", id) != 1) {
            throw new IllegalArgumentException("患者账号不存在");
        }
        queryById(id).ifPresent(accountCache::put);
    }

    public int size() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from user_account", Integer.class);
        return count == null ? 0 : count;
    }

    private static class UserAccountRowMapper implements RowMapper<UserAccount> {
        @Override
        public UserAccount mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new UserAccount(
                    rs.getString("id"),
                    rs.getString("username"),
                    rs.getString("password"),
                    rs.getString("phone"),
                    rs.getString("name"),
                    rs.getString("role"),
                    Arrays.stream(Optional.ofNullable(rs.getString("permissions")).orElse("").split(","))
                            .filter(item -> !item.isBlank()).toList(),
                    rs.getBoolean("real_name_verified"),
                    Optional.ofNullable(rs.getString("employee_no")).orElse(rs.getString("username")),
                    rs.getBoolean("active"),
                    rs.getTimestamp("created_at").toLocalDateTime());
        }
    }
}
