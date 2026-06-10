package com.cloudbrain.auth.repository;

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
    private final RowMapper<UserAccount> rowMapper = new UserAccountRowMapper();

    public UserAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserAccount> findByUsername(String username) {
        List<UserAccount> result = jdbcTemplate.query(
                "select * from user_account where username = ?",
                rowMapper,
                username);
        return result.stream().findFirst();
    }

    public boolean existsByUsername(String username) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from user_account where username = ?",
                Integer.class,
                username);
        return count != null && count > 0;
    }

    public UserAccount save(UserAccount account) {
        jdbcTemplate.update("""
                insert into user_account (id, username, password, phone, name, role, permissions, real_name_verified)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (username) do update set
                    password = excluded.password,
                    phone = excluded.phone,
                    name = excluded.name,
                    role = excluded.role,
                    permissions = excluded.permissions,
                    real_name_verified = excluded.real_name_verified
                """,
                account.getId(),
                account.getUsername(),
                account.getPassword(),
                account.getPhone(),
                account.getName(),
                account.getRole(),
                String.join(",", account.getPermissions()),
                account.isRealNameVerified());
        return account;
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
                    Arrays.stream(rs.getString("permissions").split(",")).filter(item -> !item.isBlank()).toList(),
                    rs.getBoolean("real_name_verified"));
        }
    }
}
