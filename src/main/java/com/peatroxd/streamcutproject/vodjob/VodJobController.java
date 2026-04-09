package com.peatroxd.streamcutproject.vodjob;

import com.peatroxd.streamcutproject.clipcandidate.api.ClipCandidateResponse;
import com.peatroxd.streamcutproject.vodjob.api.CreateJobByUrlRequest;
import com.peatroxd.streamcutproject.vodjob.api.JobDetailResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobEventResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobListItemResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobSummaryResponse;
import com.peatroxd.streamcutproject.vodjob.api.TranscriptSegmentResponse;
import com.peatroxd.streamcutproject.vodjob.api.WorkerExecutionResponse;
import com.peatroxd.streamcutproject.vodjob.api.WorkerTaskResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class VodJobController {

    private final VodJobService vodJobService;

    @PostMapping("/url")
    @ResponseStatus(HttpStatus.CREATED)
    public JobSummaryResponse createUrlJob(@Valid @RequestBody CreateJobByUrlRequest request) {
        return vodJobService.createUrlJob(request.url());
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public JobSummaryResponse createUploadJob(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "uploaded file must not be empty"
            );
        }
        return vodJobService.createFileJob(file);
    }

    @GetMapping
    public List<JobListItemResponse> listJobs() {
        return vodJobService.listJobs();
    }

    @GetMapping("/{id}")
    public JobDetailResponse getJob(@PathVariable Long id) {
        return vodJobService.getJob(id);
    }

    @PostMapping("/{id}/retry")
    public JobDetailResponse retryJob(@PathVariable Long id) {
        return vodJobService.retryJob(id);
    }

    @PostMapping("/{id}/cancel")
    public JobDetailResponse cancelJob(@PathVariable Long id) {
        return vodJobService.cancelJob(id);
    }

    @PostMapping("/{id}/force-fail")
    public JobDetailResponse forceFailJob(@PathVariable Long id) {
        return vodJobService.forceFailJob(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteJob(@PathVariable Long id) {
        vodJobService.deleteJob(id);
    }

    @GetMapping("/{id}/candidates")
    public List<ClipCandidateResponse> listCandidates(@PathVariable Long id) {
        return vodJobService.listCandidates(id);
    }

    @GetMapping("/{id}/transcript")
    public List<TranscriptSegmentResponse> listTranscriptSegments(@PathVariable Long id) {
        return vodJobService.listTranscriptSegments(id);
    }

    @GetMapping("/{id}/events")
    public List<JobEventResponse> listJobEvents(@PathVariable Long id) {
        return vodJobService.listJobEvents(id);
    }

    @GetMapping("/{id}/executions")
    public List<WorkerExecutionResponse> listWorkerExecutions(@PathVariable Long id) {
        return vodJobService.listWorkerExecutions(id);
    }

    @GetMapping("/{id}/tasks")
    public List<WorkerTaskResponse> listWorkerTasks(@PathVariable Long id) {
        return vodJobService.listWorkerTasks(id);
    }
}
