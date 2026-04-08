package com.peatroxd.streamcutproject.e2e;

import jakarta.validation.constraints.NotBlank;

public record CreateE2eJobRequest(
        @NotBlank(message = "status must not be blank")
        String status,
        String sourceUrl
) {
}
