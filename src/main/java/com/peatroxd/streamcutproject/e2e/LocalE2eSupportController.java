package com.peatroxd.streamcutproject.e2e;

import com.peatroxd.streamcutproject.vodjob.api.JobDetailResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
@RequestMapping("/api/internal/e2e")
@RequiredArgsConstructor
public class LocalE2eSupportController {

    private final LocalE2eSupportService localE2eSupportService;

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetState() {
        localE2eSupportService.resetState();
    }

    @PostMapping("/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    public JobDetailResponse createJob(@Valid @RequestBody CreateE2eJobRequest request) {
        return localE2eSupportService.createJob(request);
    }
}
