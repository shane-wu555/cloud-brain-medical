package com.cloudbrain.medicalorder.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class MinioStorageServiceTest {
    @Test
    void putCreatesBucketWhenNeededAndGetReturnsStream() throws Exception {
        MinioStorageService service = new MinioStorageService("http://minio", "access", "secret", "bucket");
        MinioClient client = Mockito.mock(MinioClient.class);
        ReflectionTestUtils.setField(service, "client", client);
        when(client.bucketExists(any())).thenReturn(false);
        GetObjectResponse stream = Mockito.mock(GetObjectResponse.class);
        when(client.getObject(any())).thenReturn(stream);

        assertThat(service.put("orders/a.png", new ByteArrayInputStream(new byte[] {1, 2, 3}), 3, "image/png")).isEqualTo("bucket");
        assertThat(service.get("orders/a.png")).isSameAs(stream);
    }

    @Test
    void putAndGetWrapStorageFailures() throws Exception {
        MinioStorageService service = new MinioStorageService("http://minio", "access", "secret", "bucket");
        MinioClient client = Mockito.mock(MinioClient.class);
        ReflectionTestUtils.setField(service, "client", client);
        when(client.bucketExists(any())).thenThrow(new IllegalStateException("down"));
        when(client.getObject(any())).thenThrow(new IllegalStateException("down"));

        assertThatThrownBy(() -> service.put("orders/a.png", new ByteArrayInputStream(new byte[] {1}), 1, "image/png"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.get("orders/a.png")).isInstanceOf(IllegalStateException.class);
    }
}
