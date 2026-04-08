package com.peatroxd.streamcutproject.workerdispatch;

import com.peatroxd.streamcutproject.vodjob.VodJobService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/worker")
public class WorkerResultController {

    private final VodJobService vodJobService;

    public WorkerResultController(VodJobService vodJobService) {
        this.vodJobService = vodJobService;
    }

    @PostMapping("/results")
    public WorkerTransportAck submitResult(@Valid @RequestBody WorkerProcessingResultPayload payload) {
        return vodJobService.ingestWorkerResult(payload);
    }

    @PostMapping("/downloads/results")
    public WorkerTransportAck submitDownloadResult(@Valid @RequestBody WorkerDownloadResultPayload payload) {
        return vodJobService.ingestWorkerDownloadResult(payload);
    }

    @PostMapping("/progress")
    public WorkerTransportAck submitProgress(@Valid @RequestBody WorkerProgressUpdatePayload payload) {
        return vodJobService.updateWorkerProgress(payload);
    }

    @PostMapping("/exports/results")
    public WorkerTransportAck submitExportResult(@Valid @RequestBody WorkerExportResultPayload payload) {
        return vodJobService.ingestWorkerExportResult(payload);
    }

    @PostMapping("/failures")
    public WorkerTransportAck submitFailure(@Valid @RequestBody WorkerFailureReportPayload payload) {
        return vodJobService.reportWorkerFailure(payload);
    }
}
