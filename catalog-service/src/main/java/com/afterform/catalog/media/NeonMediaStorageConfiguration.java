package com.afterform.catalog.media;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** Uses Neon AWS_* environment variables with the official AWS SDK. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "AFTERFORM_MEDIA_BUCKET")
public class NeonMediaStorageConfiguration {
    @Bean(destroyMethod = "close")
    S3Client neonS3Client(@Value("${AWS_ENDPOINT_URL_S3}") String endpoint,
                         @Value("${AWS_REGION}") String region) {
        return S3Client.builder().endpointOverride(URI.create(endpoint))
            .region(Region.of(region))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner neonS3Presigner(@Value("${AWS_ENDPOINT_URL_S3}") String endpoint,
                               @Value("${AWS_REGION}") String region) {
        return S3Presigner.builder().endpointOverride(URI.create(endpoint))
            .region(Region.of(region))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build();
    }

    @Bean
    NeonMediaStorage neonMediaStorage(S3Client neonS3Client, S3Presigner neonS3Presigner,
                                     @Value("${AFTERFORM_MEDIA_BUCKET}") String bucket) {
        return new NeonMediaStorage(neonS3Client, neonS3Presigner, bucket);
    }
}
