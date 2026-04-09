package com.peatroxd.streamcutproject.storage;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3ArtifactStorageServiceTest {

    @Test
    void rejectsMissingPublicEndpointInS3Mode() {
        ArtifactStorageProperties properties = new ArtifactStorageProperties();
        properties.setMode(ArtifactStorageProperties.Mode.S3);
        properties.setEndpoint(URI.create("http://minio:9000"));
        properties.setAccessKey("minioadmin");
        properties.setSecretKey("minioadmin");
        properties.setBucket("streamcut-artifacts");

        assertThatThrownBy(() -> new S3ArtifactStorageService(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.artifact-storage.public-endpoint must be configured in S3 mode");
    }

    @Test
    void rejectsBlankBucketInS3Mode() {
        ArtifactStorageProperties properties = new ArtifactStorageProperties();
        properties.setMode(ArtifactStorageProperties.Mode.S3);
        properties.setEndpoint(URI.create("http://minio:9000"));
        properties.setPublicEndpoint(URI.create("http://localhost:9000"));
        properties.setAccessKey("minioadmin");
        properties.setSecretKey("minioadmin");
        properties.setBucket(" ");

        assertThatThrownBy(() -> new S3ArtifactStorageService(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.artifact-storage.bucket must be configured in S3 mode");
    }
}
