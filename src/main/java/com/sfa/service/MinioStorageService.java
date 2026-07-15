package com.sfa.service;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService {

    private final MinioClient minioClient;

    @Value("${app.minio.bucket-name}")
    private String bucket;

    public void upload(String objectPath, byte[] data, String contentType) {
        try {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectPath)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("MinIO upload failed: " + e.getMessage(), e);
        }
    }

    public byte[] download(String objectPath) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (var stream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectPath)
                    .build())) {
                stream.transferTo(out);
            }
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("MinIO download failed: " + e.getMessage(), e);
        }
    }

    public String getPresignedUrl(String objectPath, int expirySeconds) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(bucket)
                    .object(objectPath)
                    .method(Method.GET)
                    .expiry(expirySeconds)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate presigned URL", e);
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("Created MinIO bucket: {}", bucket);
        }
    }
}
