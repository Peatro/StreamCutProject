package com.peatroxd.streamcutproject.workerdispatch;

import com.peatroxd.streamcutproject.vodjob.VodJobService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/worker")
public class WorkerClaimController {

    private final VodJobService vodJobService;

    public WorkerClaimController(VodJobService vodJobService) {
        this.vodJobService = vodJobService;
    }

    @PostMapping("/claims/next")
    public ResponseEntity<WorkerDispatchPayload> claimNextJob(@Valid @RequestBody WorkerClaimRequest request) {
        return vodJobService.claimNextQueuedJob(request.workerId(), request.workerRole())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
