package com.cloudbrain.patient.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.patient.repository.PatientRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class PatientCacheServiceTest {
    @Test
    void getAccountStateReturnsCachedValueWhenPresent() {
        RedisTemplate<String, PatientRepository.PatientAccountState> redis = Mockito.mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, PatientRepository.PatientAccountState> valueOperations = Mockito.mock(ValueOperations.class);
        PatientRepository.PatientAccountState state = sampleState();
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("cloudbrain:patient:account:account-1")).thenReturn(state);

        Optional<PatientRepository.PatientAccountState> result = new PatientCacheService(redis).getAccountState("account-1");

        assertThat(result).contains(state);
    }

    @Test
    void getAccountStateReturnsEmptyWhenCacheMissOrRedisFails() {
        RedisTemplate<String, PatientRepository.PatientAccountState> redis = Mockito.mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, PatientRepository.PatientAccountState> valueOperations = Mockito.mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("cloudbrain:patient:account:account-1")).thenReturn(null);
        when(valueOperations.get("cloudbrain:patient:account:account-2"))
                .thenThrow(new IllegalStateException("boom"));
        when(valueOperations.get("cloudbrain:patient:account:account-3"))
                .thenThrow(new RedisConnectionFailureException("offline"));

        PatientCacheService service = new PatientCacheService(redis);

        assertThat(service.getAccountState("account-1")).isEmpty();
        assertThat(service.getAccountState("account-2")).isEmpty();
        assertThat(service.getAccountState("account-3")).isEmpty();
    }

    @Test
    void putAccountStateWritesValueWithTtlAndIgnoresFailures() {
        RedisTemplate<String, PatientRepository.PatientAccountState> redis = Mockito.mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, PatientRepository.PatientAccountState> valueOperations = Mockito.mock(ValueOperations.class);
        PatientRepository.PatientAccountState state = sampleState();
        when(redis.opsForValue()).thenReturn(valueOperations);

        PatientCacheService service = new PatientCacheService(redis);
        service.putAccountState("account-1", state);

        verify(valueOperations).set("cloudbrain:patient:account:account-1", state, Duration.ofMinutes(10));

        service.putAccountState("account-2", null);
        verify(valueOperations, never()).set("cloudbrain:patient:account:account-2", null, Duration.ofMinutes(10));

        doThrow(new IllegalStateException("boom"))
                .when(valueOperations)
                .set("cloudbrain:patient:account:account-3", state, Duration.ofMinutes(10));
        service.putAccountState("account-3", state);
    }

    @Test
    void evictAccountDeletesKeyAndSwallowsFailures() {
        RedisTemplate<String, PatientRepository.PatientAccountState> redis = Mockito.mock(RedisTemplate.class);
        doThrow(new IllegalStateException("boom"))
                .when(redis)
                .delete("cloudbrain:patient:account:account-2");

        PatientCacheService service = new PatientCacheService(redis);
        service.evictAccount("account-1");
        service.evictAccount("account-2");

        verify(redis).delete("cloudbrain:patient:account:account-1");
        verify(redis).delete("cloudbrain:patient:account:account-2");
    }

    private PatientRepository.PatientAccountState sampleState() {
        PatientRepository.PatientProfile profile = new PatientRepository.PatientProfile(
                "patient-1",
                "account-1",
                "13800000000",
                "Patient",
                "ID_CARD",
                "ID-1",
                "F",
                LocalDate.of(1990, 1, 1),
                OffsetDateTime.now(),
                null);
        return new PatientRepository.PatientAccountState(List.of(profile), profile);
    }
}
