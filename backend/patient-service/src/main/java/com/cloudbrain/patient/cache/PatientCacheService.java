package com.cloudbrain.patient.cache;

import com.cloudbrain.patient.repository.PatientRepository;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class PatientCacheService {
    private static final Logger log = LoggerFactory.getLogger(PatientCacheService.class);
    private static final String PREFIX = "cloudbrain:patient:";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final RedisTemplate<String, PatientRepository.PatientAccountState> redis;

    public PatientCacheService(RedisTemplate<String, PatientRepository.PatientAccountState> redis) {
        this.redis = redis;
    }

    public Optional<PatientRepository.PatientAccountState> getAccountState(String accountId) {
        String key = PREFIX + "account:" + accountId;
        try {
            PatientRepository.PatientAccountState cached = redis.opsForValue().get(key);
            if (cached != null) return Optional.of(cached);
        } catch (RuntimeException e) {
            if (!(e instanceof RedisConnectionFailureException)) {
                log.debug("Redis read failed for account {}: {}", accountId, e.getMessage());
            }
        }
        return Optional.empty();
    }

    public void putAccountState(String accountId, PatientRepository.PatientAccountState state) {
        if (state == null) return;
        try {
            redis.opsForValue().set(PREFIX + "account:" + accountId, state, TTL);
        } catch (RuntimeException e) {
            log.debug("Redis write failed for account {}: {}", accountId, e.getMessage());
        }
    }

    public void evictAccount(String accountId) {
        try {
            redis.delete(PREFIX + "account:" + accountId);
        } catch (RuntimeException e) {
            log.debug("Redis evict failed for account {}: {}", accountId, e.getMessage());
        }
    }
}
