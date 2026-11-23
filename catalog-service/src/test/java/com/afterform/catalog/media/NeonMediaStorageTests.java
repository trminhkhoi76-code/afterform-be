package com.afterform.catalog.media;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** SDK-level SigV4 presign test; no real cloud credentials or HTTP calls. */
class NeonMediaStorageTests {
    @Test
    void presignsTenantScopedPrivateImageKey() {
        var credentials = StaticCredentialsProvider.create(
            AwsBasicCredentials.create("local-test", "local-test-secret"));
        var serviceConfig = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        var endpoint = URI.create("https://storage.example.test");
        try (S3Client client = S3Client.builder().region(Region.US_EAST_2)
                .endpointOverride(endpoint).credentialsProvider(credentials)
                .serviceConfiguration(serviceConfig).build();
             S3Presigner presigner = S3Presigner.builder().region(Region.US_EAST_2)
                .endpointOverride(endpoint).credentialsProvider(credentials)
                .serviceConfiguration(serviceConfig).build()) {
            var store = UUID.randomUUID();
            var media = UUID.randomUUID();
            var storage = new NeonMediaStorage(client, presigner, "afterform-media");
            var url = storage.presignUpload(store, media, 2048, "image/webp");
            assertEquals("https", url.getProtocol());
            assertTrue(url.getPath().contains("/afterform-media/stores/" + store + "/media/" + media));
            assertTrue(url.getQuery().contains("X-Amz-Signature"));
            assertThrows(IllegalArgumentException.class, () ->
                storage.presignUpload(store, media, 0, "image/jpeg"));
            assertThrows(IllegalArgumentException.class, () ->
                storage.presignUpload(store, media, 2048, "application/octet-stream"));
        }
    }
}
