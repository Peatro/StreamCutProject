package com.peatroxd.streamcutproject.workerdispatch;

import jakarta.validation.constraints.NotBlank;

public record WorkerClaimRequest(
        @NotBlank(message = "workerId must not be blank")
        String workerId,
        @NotBlank(message = "workerRole must not be blank")
        String workerRole
) {
}
