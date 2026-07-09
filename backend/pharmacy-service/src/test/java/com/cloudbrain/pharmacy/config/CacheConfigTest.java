package com.cloudbrain.pharmacy.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

class CacheConfigTest {
    private final CacheConfig config = new CacheConfig();

    @Test
    void cacheErrorHandlerSwallowsAllCacheOperationErrors() {
        CacheErrorHandler handler = config.cacheErrorHandler();
        Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn("drugs");

        assertThatCode(() -> handler.handleCacheGetError(new RuntimeException("read"), cache, "drug-1")).doesNotThrowAnyException();
        assertThatCode(() -> handler.handleCachePutError(new RuntimeException("write"), cache, "drug-1", "value")).doesNotThrowAnyException();
        assertThatCode(() -> handler.handleCacheEvictError(new RuntimeException("evict"), cache, "drug-1")).doesNotThrowAnyException();
        assertThatCode(() -> handler.handleCacheClearError(new RuntimeException("clear"), cache)).doesNotThrowAnyException();
    }
}
