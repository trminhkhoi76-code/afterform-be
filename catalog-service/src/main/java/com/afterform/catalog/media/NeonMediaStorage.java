package com.afterform.catalog.media;

import java.net.URL;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/** Internal-only adapter. Public upload must wait for authenticated admin APIs. */
public final class NeonMediaStorage {
    private static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;
    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    public NeonMediaStorage(S3Client s3, S3Presigner presigner, String bucket) {
        this.s3 = Objects.requireNonNull(s3);
        this.presigner = Objects.requireNonNull(presigner);
        if (bucket == null || !bucket.matches("[a-z0-9][a-z0-9.-]{2,62}")) {
            throw new IllegalArgumentException("Invalid private storage bucket");
        }
        this.bucket = bucket;
    }

    public String uploadImage(UUID storeId, UUID mediaId, byte[] data, String contentType) {
        Objects.requireNonNull(data);
        validate(contentType, data.length);
        String key = key(storeId, mediaId);
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
            .contentType(contentType).build(), RequestBody.fromBytes(data));
        return key;
    }

    public URL presignUpload(UUID storeId, UUID mediaId, long sizeBytes, String contentType) {
        validate(contentType, sizeBytes);
        var request = PutObjectRequest.builder()
            .bucket(bucket).key(key(storeId, mediaId))
            .contentType(contentType).contentLength(sizeBytes).build();
        return presigner.presignPutObject(PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(5))
            .putObjectRequest(request).build()).url();
    }

    public byte[] readImage(UUID storeId, UUID mediaId) {
        return s3.getObjectAsBytes(GetObjectRequest.builder()
            .bucket(bucket).key(key(storeId, mediaId)).build()).asByteArray();
    }

    public void deleteImage(UUID storeId, UUID mediaId) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket)
            .key(key(storeId, mediaId)).build());
    }

    private static String key(UUID storeId, UUID mediaId) {
        return "stores/" + Objects.requireNonNull(storeId) + "/media/" + Objects.requireNonNull(mediaId);
    }

    private static void validate(String contentType, long sizeBytes) {
        if (!IMAGE_TYPES.contains(contentType))
            throw new IllegalArgumentException("Unsupported image type");
        if (sizeBytes <= 0 || sizeBytes > MAX_UPLOAD_BYTES)
            throw new IllegalArgumentException("Image size must be 1..5 MiB");
    }
}
