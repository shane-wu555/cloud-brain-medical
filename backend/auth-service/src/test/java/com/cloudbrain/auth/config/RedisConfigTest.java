package com.cloudbrain.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.cloudbrain.auth.entity.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

class RedisConfigTest {
    private final RedisConfig config = new RedisConfig();

    @Test
    void userAccountRedisTemplateUsesExpectedSerializers() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

        RedisTemplate<String, UserAccount> template = config.userAccountRedisTemplate(connectionFactory);

        assertThat(template.getConnectionFactory()).isSameAs(connectionFactory);
        assertThat(template.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getHashKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getValueSerializer()).isInstanceOf(Jackson2JsonRedisSerializer.class);
        assertThat(template.getHashValueSerializer()).isInstanceOf(Jackson2JsonRedisSerializer.class);
    }
}
