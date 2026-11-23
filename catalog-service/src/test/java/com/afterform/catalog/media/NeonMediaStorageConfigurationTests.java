package com.afterform.catalog.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** DB-only deployments must not create any S3 infrastructure. */
class NeonMediaStorageConfigurationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(NeonMediaStorageConfiguration.class));

    @Test
    void disabledByDefaultWithoutAnyStorageSecrets() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).doesNotHaveBean(NeonMediaStorage.class);
            assertThat(ctx).doesNotHaveBean(NeonMediaStorageConfiguration.class);
        });
    }

    @Test
    void enablingStorageWithoutEndpointFailsClearly() {
        runner.withPropertyValues("afterform.media.enabled=true", "afterform.media.bucket=afterform-media")
            .run(ctx -> assertThat(ctx).hasFailed());
    }
}
