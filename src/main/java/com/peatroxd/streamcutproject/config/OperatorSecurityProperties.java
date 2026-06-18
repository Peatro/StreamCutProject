package com.peatroxd.streamcutproject.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.security")
@Getter
@Setter
public class OperatorSecurityProperties {

    @NotBlank
    private String operatorUsername;

    @NotBlank
    private String operatorPassword;

    @NotBlank
    private String rememberMeKey;

    private Duration rememberMeValidity = Duration.ofDays(30);

}
