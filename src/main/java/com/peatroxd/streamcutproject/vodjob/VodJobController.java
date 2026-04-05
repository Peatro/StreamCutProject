package com.peatroxd.streamcutproject.vodjob;

import com.peatroxd.streamcutproject.vodjob.api.CreateJobByUrlRequest;
import com.peatroxd.streamcutproject.vodjob.api.JobEventResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobListItemResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class VodJobController {

    private final VodJobService vodJobService;

    public VodJobController(VodJobService vodJobService) {
        this.vodJobService = vodJobService;
    }

    @PostMapping("/url")
    @ResponseStatus(HttpStatus.CREATED)
    public JobSummaryResponse createUrlJob(@Valid @RequestBody CreateJobByUrlRequest request) {
        return vodJobService.createUrlJob(request.url());
    }

    @GetMapping
    public List<JobListItemResponse> listJobs() {
        return vodJobService.listJobs();
    }

    @GetMapping("/{id}/events")
    public List<JobEventResponse> listJobEvents(@org.springframework.web.bind.annotation.PathVariable Long id) {
        return vodJobService.listJobEvents(id);
    }
}
