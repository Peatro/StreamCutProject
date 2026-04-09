package com.peatroxd.streamcutproject.workerdispatch;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WorkerFailureReportPayload(
        @NotNull(message = "executionId must not be null")
        Long executionId,
        @NotNull(message = "jobId must not be null")
        Long jobId,
        @NotBlank(message = "workerId must not be blank")
        String workerId,
        @NotNull(message = "processingVersion must not be null")
        Long processingVersion,
        @NotBlank(message = "failedState must not be blank")
        String failedState,
        @NotBlank(message = "message must not be blank")
        String message
) {
}
