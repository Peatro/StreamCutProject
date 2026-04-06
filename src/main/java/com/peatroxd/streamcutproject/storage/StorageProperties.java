package com.peatroxd.streamcutproject.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;

@Validated
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    @NotNull
    private Path localRoot;

    public Path getLocalRoot() {
        return localRoot;
    }

    public void setLocalRoot(Path localRoot) {
        this.localRoot = localRoot;
    }
}
