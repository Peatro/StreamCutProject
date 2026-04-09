package com.peatroxd.streamcutproject.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.nio.file.Path;

@Validated
@ConfigurationProperties(prefix = "app.storage")
@Getter
@Setter
public class StorageProperties {

    @NotNull
    private Path localRoot;

}
