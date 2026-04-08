package com.peatroxd.streamcutproject.workerdispatch;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WorkerProgressUpdatePayload(
        @NotNull(message = "executionId must not be null")
        Long executionId,
        @NotNull(message = "jobId must not be null")
        Long jobId,
        @NotBlank(message = "workerId must not be blank")
        String workerId,
        @NotNull(message = "processingVersion must not be null")
        Long processingVersion,
        @NotBlank(message = "status must not be blank")
        String status,
        @NotNull(message = "progressPercent must not be null")
        @Min(value = 0, message = "progressPercent must be at least 0")
        @Max(value = 100, message = "progressPercent must be at most 100")
        Integer progressPercent,
        @NotBlank(message = "message must not be blank")
        String message
) {
}
