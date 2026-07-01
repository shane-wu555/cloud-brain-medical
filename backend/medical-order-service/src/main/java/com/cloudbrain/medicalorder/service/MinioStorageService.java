package com.cloudbrain.medicalorder.service;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MinioStorageService {
    private final MinioClient client;
    private final String bucket;

    public MinioStorageService(
            @Value("${storage.minio.endpoint}") String endpoint,
            @Value("${storage.minio.access-key}") String access,
            @Value("${storage.minio.secret-key}") String secret,
            @Value("${storage.minio.bucket}") String bucket) {
        this.client = MinioClient.builder().endpoint(endpoint).credentials(access, secret).build();
        this.bucket = bucket;
    }

    public String put(String objectKey, InputStream stream, long size, String contentType) {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(stream, size, -1)
                    .contentType(contentType)
                    .build());
            return bucket;
        } catch (Exception e) {
            throw new IllegalStateException("附件上传 MinIO 失败: " + e.getMessage(), e);
        }
    }

    public InputStream get(String objectKey) {
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            throw new IllegalStateException("附件读取 MinIO 失败: " + e.getMessage(), e);
        }
    }
}
