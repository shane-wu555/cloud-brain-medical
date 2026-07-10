package com.cloudbrain.doctor.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

class CacheConfigTest {
    @Test
    void cacheConfigCarriesExpectedAnnotations() {
        assertThat(CacheConfig.class.isAnnotationPresent(Configuration.class)).isTrue();
        assertThat(CacheConfig.class.isAnnotationPresent(EnableCaching.class)).isTrue();
    }
}
