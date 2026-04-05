package com.peatroxd.streamcutproject.vodjob.api;

import jakarta.validation.constraints.NotBlank;

public record CreateJobByUrlRequest(
        @NotBlank(message = "url must not be blank")
        String url
) {
}
