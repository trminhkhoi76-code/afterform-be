package com.afterform.catalog.media;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** Opt-in real Neon S3 PUT -> GET -> DELETE. Never runs on normal CI or PR builds. */
class NeonMediaStorageLiveTests {
    @Test
    @EnabledIfEnvironmentVariable(named = "NEON_LIVE_STORAGE_TEST", matches = "true")
    void roundTripsImageInPrivateNeonBucket() {
        var endpoint = URI.create(require("AWS_ENDPOINT_URL_S3"));
        var region = Region.of(require("AWS_REGION"));
        var bucket = require("AFTERFORM_MEDIA_BUCKET");
        var config = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        try (S3Client client = S3Client.builder().endpointOverride(endpoint)
                .region(region).serviceConfiguration(config).build();
             S3Presigner presigner = S3Presigner.builder().endpointOverride(endpoint)
                .region(region).serviceConfiguration(config).build()) {
            var storage = new NeonMediaStorage(client, presigner, bucket);
            var storeId = UUID.randomUUID();
            var mediaId = UUID.randomUUID();
            byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+Xq/0AAAAASUVORK5CYII=");
            try {
                String key = storage.uploadImage(storeId, mediaId, png, "image/png");
                assertTrue(key.contains(storeId.toString()));
                assertArrayEquals(png, storage.readImage(storeId, mediaId));
            } finally {
                storage.deleteImage(storeId, mediaId);
            }
        }
    }

    private static String require(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required Neon storage environment variable: " + key);
        }
        return value;
    }
}
