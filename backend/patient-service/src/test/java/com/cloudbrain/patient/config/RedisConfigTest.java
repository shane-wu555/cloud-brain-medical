package com.cloudbrain.patient.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudbrain.patient.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

class RedisConfigTest {
    private final RedisConfig config = new RedisConfig();

    @Test
    void patientRedisTemplateUsesExpectedSerializers() {
        RedisConnectionFactory connectionFactory = Mockito.mock(RedisConnectionFactory.class);

        RedisTemplate<String, PatientRepository.PatientAccountState> template =
                config.patientRedisTemplate(connectionFactory);

        assertThat(template.getConnectionFactory()).isSameAs(connectionFactory);
        assertThat(template.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getHashKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getValueSerializer()).isInstanceOf(Jackson2JsonRedisSerializer.class);
        assertThat(template.getHashValueSerializer()).isInstanceOf(Jackson2JsonRedisSerializer.class);
    }
}
