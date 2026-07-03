package com.cloudbrain.auth.cache;

import com.cloudbrain.auth.entity.UserAccount;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuthAccountCacheService {
    private static final Logger log = LoggerFactory.getLogger(AuthAccountCacheService.class);
    private static final String PREFIX = "cloudbrain:auth:account:";

    private final RedisTemplate<String, UserAccount> redis;
    private final Duration ttl;

    public AuthAccountCacheService(RedisTemplate<String, UserAccount> redis,
            @Value("${security.cache.account-ttl-seconds:600}") long ttlSeconds) {
        this.redis = redis;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public Optional<UserAccount> findByUsername(String username, Supplier<Optional<UserAccount>> loader) {
        return find("username", username, loader);
    }

    public Optional<UserAccount> findByEmployeeNo(String employeeNo, Supplier<Optional<UserAccount>> loader) {
        return find("employee", employeeNo, loader);
    }

    public Optional<UserAccount> findByPhone(String phone, Supplier<Optional<UserAccount>> loader) {
        return find("phone", phone, loader);
    }

    public Optional<UserAccount> findById(String id, Supplier<Optional<UserAccount>> loader) {
        return find("id", id, loader);
    }

    public void put(UserAccount account) {
        if (account == null) return;
        try {
            putIfPresent(key("id", account.getId()), account);
            putIfPresent(key("username", account.getUsername()), account);
            putIfPresent(key("phone", account.getPhone()), account);
            putIfPresent(key("employee", account.getEmployeeNo()), account);
        } catch (RuntimeException exception) {
            log.debug("Redis account cache put failed; continuing without cache: {}", exception.getMessage());
        }
    }

    public void evict(UserAccount account) {
        if (account == null) return;
        evictKeys(
                key("id", account.getId()),
                key("username", account.getUsername()),
                key("phone", account.getPhone()),
                key("employee", account.getEmployeeNo()));
    }

    public void evictById(String id) {
        if (id == null || id.isBlank()) return;
        evictKeys(key("id", id));
    }

    private Optional<UserAccount> find(String namespace, String value, Supplier<Optional<UserAccount>> loader) {
        if (value == null || value.isBlank()) return loader.get();
        try {
            UserAccount cached = redis.opsForValue().get(key(namespace, value));
            if (cached != null) return Optional.of(cached);
        } catch (RuntimeException exception) {
            if (!(exception instanceof RedisConnectionFailureException)) {
                log.debug("Redis account cache read failed; falling back to database: {}", exception.getMessage());
            }
        }
        Optional<UserAccount> loaded = loader.get();
        loaded.ifPresent(this::put);
        return loaded;
    }

    private void putIfPresent(String key, UserAccount account) {
        if (key != null) redis.opsForValue().set(key, account, ttl);
    }

    private void evictKeys(String... keys) {
        for (String key : keys) {
            if (key == null) continue;
            try {
                redis.delete(key);
            } catch (RuntimeException exception) {
                log.debug("Redis account cache eviction failed; continuing: {}", exception.getMessage());
            }
        }
    }

    private String key(String namespace, String value) {
        if (value == null || value.isBlank()) return null;
        return PREFIX + namespace + ":" + value.trim();
    }
}
