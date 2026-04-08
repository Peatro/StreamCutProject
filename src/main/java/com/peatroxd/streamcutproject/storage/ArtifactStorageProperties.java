package com.peatroxd.streamcutproject.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.artifact-storage")
@Getter
@Setter
public class ArtifactStorageProperties {

    private Mode mode = Mode.LOCAL;
    private URI endpoint;
    private URI publicEndpoint;
    private String region = "us-east-1";
    private String accessKey;
    private String secretKey;
    private String bucket = "streamcut-artifacts";
    private Duration presignTtl = Duration.ofMinutes(15);

    public enum Mode {
        LOCAL,
        S3
    }
}
