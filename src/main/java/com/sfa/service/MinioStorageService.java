package com.sfa.service;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * File storage for invoice PDFs and company logos. Normally MinIO; on a developer machine with
 * no MinIO running, files go to a local folder instead.
 *
 * <p>{@code app.storage.type}: {@code minio} (MinIO only), {@code local} (folder only), or
 * {@code auto} (default) — MinIO, falling back to the folder only when the MinIO endpoint is on
 * this machine (localhost) and can't be reached. A remote endpoint (e.g. {@code http://minio:9000}
 * in Docker) never falls back, so a MinIO outage in production can't silently put files on an
 * ephemeral container disk. Reads check the folder first, then MinIO.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService {

    private final MinioClient minioClient;

    @Value("${app.minio.bucket-name}")
    private String bucket;

    @Value("${app.minio.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${app.storage.type:auto}")
    private String storageType;

    @Value("${app.storage.local-dir:${user.home}/sfa-storage}")
    private String localDir;

    private final AtomicBoolean warned = new AtomicBoolean();

    public void upload(String objectPath, byte[] data, String contentType) {
        String mode = mode();
        if (mode.equals("local")) {
            writeLocal(objectPath, data);
            return;
        }
        try {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectPath)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            if (canFallBack(mode)) {
                if (warned.compareAndSet(false, true)) {
                    log.warn("MinIO at {} is not reachable ({}); storing files in {} instead",
                            endpoint, e.getMessage(), localRoot());
                }
                writeLocal(objectPath, data);
                return;
            }
            throw new RuntimeException("MinIO upload failed: " + e.getMessage(), e);
        }
    }

    public byte[] download(String objectPath) {
        String mode = mode();
        Path local = localPath(objectPath);
        if (!mode.equals("minio") && Files.isRegularFile(local)) {
            try {
                return Files.readAllBytes(local);
            } catch (IOException e) {
                throw new RuntimeException("Reading stored file failed: " + e.getMessage(), e);
            }
        }
        if (mode.equals("local")) {
            throw new RuntimeException("Stored file not found: " + objectPath);
        }
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

    // ── Internals ───────────────────────────────────────────────────────────────

    private String mode() {
        String m = storageType == null ? "auto" : storageType.trim().toLowerCase(Locale.ROOT);
        return m.equals("minio") || m.equals("local") ? m : "auto";
    }

    /** Auto mode falls back to the folder only for a MinIO endpoint on this machine. */
    private boolean canFallBack(String mode) {
        if (!mode.equals("auto")) return false;
        try {
            String host = URI.create(endpoint).getHost();
            return host != null && (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1")
                    || host.equals("::1") || host.equals("[::1]"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private Path localRoot() {
        return Paths.get(localDir, bucket).toAbsolutePath().normalize();
    }

    /** The object's file under the storage folder; refuses paths that would leave it. */
    private Path localPath(String objectPath) {
        Path root = localRoot();
        Path p = root.resolve(objectPath.replace('\\', '/').replaceAll("^/+", "")).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage path: " + objectPath);
        }
        return p;
    }

    private void writeLocal(String objectPath, byte[] data) {
        Path p = localPath(objectPath);
        try {
            Files.createDirectories(p.getParent());
            Path tmp = Files.createTempFile(p.getParent(), ".upload", ".tmp");
            Files.write(tmp, data);
            Files.move(tmp, p, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new RuntimeException("Saving file failed: " + e.getMessage(), e);
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
