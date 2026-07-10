package com.cloudbrain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.auth.cache.AuthAccountCacheService;
import com.cloudbrain.auth.entity.UserAccount;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class UserAccountRepositoryTest {
    @Mock
    JdbcTemplate jdbcTemplate;

    @Mock
    AuthAccountCacheService accountCache;

    @Test
    void findByUsernameUsesLoaderAndReturnsFirstResult() {
        UserAccountRepository repository = new UserAccountRepository(jdbcTemplate, accountCache);
        UserAccount account = account("user-1", "PATIENT");
        when(accountCache.findByUsername(eq("user-1"), any())).thenAnswer(invocation ->
                ((Supplier<Optional<UserAccount>>) invocation.getArgument(1)).get());
        when(jdbcTemplate.query(eq("select * from user_account where username = ?"), any(RowMapper.class), eq("user-1")))
                .thenReturn(List.of(account));

        Optional<UserAccount> result = repository.findByUsername("user-1");

        assertThat(result).contains(account);
    }

    @Test
    void staffAccountsAddsOptionalRoleFilter() {
        UserAccountRepository repository = new UserAccountRepository(jdbcTemplate, accountCache);
        UserAccount account = account("doctor-1", "OUTPATIENT_DOCTOR");
        when(jdbcTemplate.query(org.mockito.ArgumentMatchers.contains("and role = ?"), any(RowMapper.class), eq("OUTPATIENT_DOCTOR")))
                .thenReturn(List.of(account));
        when(jdbcTemplate.query(eq("""
                select *
                from user_account
                where role <> 'PATIENT'
                 order by active desc, created_at desc, username"""), any(RowMapper.class), new Object[0]))
                .thenReturn(List.of(account));

        assertThat(repository.staffAccounts("OUTPATIENT_DOCTOR")).containsExactly(account);
        assertThat(repository.staffAccounts(null)).containsExactly(account);
    }

    @Test
    void saveEvictsOldCacheAndPutsNewValue() {
        UserAccountRepository repository = new UserAccountRepository(jdbcTemplate, accountCache);
        UserAccount existing = account("user-1", "PATIENT");
        UserAccount updated = account("user-1", "OUTPATIENT_DOCTOR");
        when(jdbcTemplate.query(eq("select * from user_account where username = ?"), any(RowMapper.class), eq("user-1")))
                .thenReturn(List.of(existing));

        UserAccount result = repository.save(updated);

        assertThat(result).isSameAs(updated);
        verify(accountCache).evict(existing);
        verify(accountCache).put(updated);
    }

    @Test
    void updateStaffOperationsReloadAccountOrThrow() {
        UserAccountRepository repository = new UserAccountRepository(jdbcTemplate, accountCache);
        UserAccount updated = account("doctor-1", "OUTPATIENT_DOCTOR");
        when(jdbcTemplate.query(eq("select * from user_account where id = ?"), any(RowMapper.class), eq("doctor-1")))
                .thenReturn(List.of(updated));
        when(jdbcTemplate.update(org.mockito.ArgumentMatchers.contains("where id = ? and role <> 'PATIENT'"),
                eq("Doctor"), eq("13800000000"), eq("OUTPATIENT_DOCTOR"), eq("doctor-1")))
                .thenReturn(1);
        when(jdbcTemplate.update("update user_account set password = ? where id = ? and role <> 'PATIENT'",
                "encoded", "doctor-1"))
                .thenReturn(1);
        when(jdbcTemplate.update("update user_account set active = ? where id = ? and role <> 'PATIENT'",
                false, "doctor-1"))
                .thenReturn(1);
        when(jdbcTemplate.update("update user_account set real_name_verified = true where id = ? and role = 'PATIENT'",
                "doctor-1"))
                .thenReturn(1);

        assertThat(repository.updateStaffProfile("doctor-1", "Doctor", "13800000000", "OUTPATIENT_DOCTOR")).isEqualTo(updated);
        assertThat(repository.updateStaffPassword("doctor-1", "encoded")).isEqualTo(updated);
        assertThat(repository.setStaffActive("doctor-1", false)).isEqualTo(updated);
        repository.markRealNameVerified("doctor-1");

        when(jdbcTemplate.update("update user_account set password = ? where id = ? and role <> 'PATIENT'",
                "bad", "missing"))
                .thenReturn(0);
        assertThatThrownBy(() -> repository.updateStaffPassword("missing", "bad")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updatePasswordAndSizeHandleNullCount() {
        UserAccountRepository repository = new UserAccountRepository(jdbcTemplate, accountCache);
        when(jdbcTemplate.query(eq("select * from user_account where id = ?"), any(RowMapper.class), eq("user-1")))
                .thenReturn(List.of(account("user-1", "PATIENT")));
        when(jdbcTemplate.queryForObject("select count(*) from user_account", Integer.class)).thenReturn(null);

        repository.updatePassword("user-1", "encoded");

        assertThat(repository.size()).isZero();
    }

    @Test
    void rowMapperSplitsPermissionsAndDefaultsEmployeeNumber() throws Exception {
        ResultSet resultSet = org.mockito.Mockito.mock(ResultSet.class);
        when(resultSet.getString("id")).thenReturn("user-1");
        when(resultSet.getString("username")).thenReturn("user-1");
        when(resultSet.getString("password")).thenReturn("encoded");
        when(resultSet.getString("phone")).thenReturn("13800000000");
        when(resultSet.getString("name")).thenReturn("Alice");
        when(resultSet.getString("role")).thenReturn("PATIENT");
        when(resultSet.getString("permissions")).thenReturn("a,b,,");
        when(resultSet.getBoolean("real_name_verified")).thenReturn(true);
        when(resultSet.getString("employee_no")).thenReturn(null);
        when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.valueOf("2026-07-10 10:00:00"));

        java.lang.reflect.Field field = UserAccountRepository.class.getDeclaredField("rowMapper");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        RowMapper<UserAccount> rowMapper = (RowMapper<UserAccount>) field.get(new UserAccountRepository(jdbcTemplate, accountCache));

        UserAccount mapped = rowMapper.mapRow(resultSet, 0);

        assertThat(mapped.getPermissions()).containsExactly("a", "b");
        assertThat(mapped.getEmployeeNo()).isEqualTo("user-1");
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
