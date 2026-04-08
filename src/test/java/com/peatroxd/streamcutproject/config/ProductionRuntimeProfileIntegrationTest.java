package com.peatroxd.streamcutproject.config;

import com.peatroxd.streamcutproject.storage.ArtifactStorageProperties;
import com.peatroxd.streamcutproject.storage.StorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.profiles.active=prod",
        "SPRING_DATASOURCE_URL=jdbc:h2:mem:streamcut-prod-profile;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver",
        "SPRING_DATASOURCE_USERNAME=sa",
        "SPRING_DATASOURCE_PASSWORD=",
        "APP_STORAGE_LOCAL_ROOT=build/streamcut-prod-profile-storage",
        "APP_OPERATOR_USERNAME=prod-operator",
        "APP_OPERATOR_PASSWORD=prod-password",
        "APP_ARTIFACT_STORAGE_MODE=LOCAL",
        "APP_ARTIFACT_STORAGE_ENDPOINT=http://artifact-internal.invalid",
        "APP_ARTIFACT_STORAGE_PUBLIC_ENDPOINT=https://artifacts.example.com",
        "APP_ARTIFACT_STORAGE_ACCESS_KEY=test-access-key",
        "APP_ARTIFACT_STORAGE_SECRET_KEY=test-secret-key",
        "APP_ARTIFACT_STORAGE_BUCKET=streamcut-artifacts-prod"
})
class ProductionRuntimeProfileIntegrationTest {

    @Autowired
    private Environment environment;

    @Autowired
    private OperatorSecurityProperties operatorSecurityProperties;

    @Autowired
    private StorageProperties storageProperties;

    @Autowired
    private ArtifactStorageProperties artifactStorageProperties;

    @Test
    void bootsWithExplicitProductionInputs() {
        assertThat(environment.acceptsProfiles(Profiles.of("prod"))).isTrue();
        assertThat(operatorSecurityProperties.getOperatorUsername()).isEqualTo("prod-operator");
        assertThat(operatorSecurityProperties.getOperatorPassword()).isEqualTo("prod-password");
        assertThat(storageProperties.getLocalRoot()).isEqualTo(Path.of("build", "streamcut-prod-profile-storage"));
        assertThat(artifactStorageProperties.getMode()).isEqualTo(ArtifactStorageProperties.Mode.LOCAL);
        assertThat(artifactStorageProperties.getBucket()).isEqualTo("streamcut-artifacts-prod");
        assertThat(environment.getProperty("management.endpoints.access.default")).isEqualTo("none");
        assertThat(environment.getProperty("management.endpoint.metrics.access")).isEqualTo("read-only");
        assertThat(environment.getProperty("management.endpoint.prometheus.access")).isEqualTo("read-only");
    }
}
