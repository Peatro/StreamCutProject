package com.peatroxd.streamcutproject.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Validated
@ConfigurationProperties(prefix = "app.security")
@Getter
@Setter
public class OperatorSecurityProperties {

    @NotBlank
    private String operatorUsername;

    @NotBlank
    private String operatorPassword;

}
