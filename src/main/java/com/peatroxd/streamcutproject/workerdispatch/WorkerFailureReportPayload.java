package com.peatroxd.streamcutproject.workerdispatch;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WorkerFailureReportPayload(
        @NotNull(message = "jobId must not be null")
        Long jobId,
        @NotBlank(message = "failedState must not be blank")
        String failedState,
        @NotBlank(message = "message must not be blank")
        String message
) {
}
