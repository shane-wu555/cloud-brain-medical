package com.cloudbrain.auth.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.auth.entity.UserAccount;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class AuthAccountCacheServiceTest {
    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, UserAccount> redis = Mockito.mock(RedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, UserAccount> valueOperations = Mockito.mock(ValueOperations.class);

    private final AuthAccountCacheService service = new AuthAccountCacheService(redis, 600);

    @Test
    void cachedValueIsReturnedWithoutInvokingLoader() {
        UserAccount account = account();
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("cloudbrain:auth:account:username:doctor")).thenReturn(account);

        AtomicBoolean loaded = new AtomicBoolean(false);
        Optional<UserAccount> result = service.findByUsername("doctor", () -> {
            loaded.set(true);
            return Optional.empty();
        });

        assertThat(result).contains(account);
        assertThat(loaded).isFalse();
    }

    @Test
    void redisReadFailureFallsBackToLoaderAndCachesLoadedAccount() {
        UserAccount account = account();
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("cloudbrain:auth:account:phone:13900000000"))
                .thenThrow(new IllegalStateException("redis down"));

        Optional<UserAccount> result = service.findByPhone("13900000000", () -> Optional.of(account));

        assertThat(result).contains(account);
        verify(valueOperations).set("cloudbrain:auth:account:id:u-1", account, Duration.ofSeconds(600));
        verify(valueOperations).set("cloudbrain:auth:account:username:doctor", account, Duration.ofSeconds(600));
        verify(valueOperations).set("cloudbrain:auth:account:phone:13900000000", account, Duration.ofSeconds(600));
        verify(valueOperations).set("cloudbrain:auth:account:employee:0001", account, Duration.ofSeconds(600));
    }

    @Test
    void connectionFailureAlsoFallsBackToLoader() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("cloudbrain:auth:account:id:u-1"))
                .thenThrow(new RedisConnectionFailureException("offline"));

        Optional<UserAccount> result = service.findById("u-1", Optional::empty);

        assertThat(result).isEmpty();
    }

    @Test
    void blankLookupValueDelegatesDirectlyToLoader() {
        Optional<UserAccount> result = service.findByEmployeeNo(" ", () -> Optional.of(account()));

        assertThat(result).isPresent();
        verify(redis, never()).opsForValue();
    }

    @Test
    void putAndEvictTolerateRuntimeFailures() {
        UserAccount account = account();
        when(redis.opsForValue()).thenReturn(valueOperations);
        doThrow(new IllegalStateException("write failed"))
                .when(valueOperations)
                .set(anyString(), any(UserAccount.class), any(Duration.class));
        doThrow(new IllegalStateException("delete failed")).when(redis).delete(anyString());

        service.put(account);
        service.evict(account);
        service.evictById("u-1");
        service.put(null);
        service.evict(null);
        service.evictById(" ");

        verify(redis, times(2)).delete("cloudbrain:auth:account:id:u-1");
    }

    private UserAccount account() {
        return new UserAccount(
                "u-1",
                "doctor",
                "hash",
                "13900000000",
                "Doctor",
                "ADMIN",
                List.of("perm"),
                true,
                "0001");
    }
}
