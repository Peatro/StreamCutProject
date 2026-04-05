package com.peatroxd.streamcutproject.vodjob;

import com.peatroxd.streamcutproject.vodjob.api.JobListItemResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobDetailResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobEventResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobSummaryResponse;
import com.peatroxd.streamcutproject.vodjob.api.JobMapper;
import com.peatroxd.streamcutproject.vodjob.event.JobEventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Sort;

@Service
public class VodJobService {

    private static final String SOURCE_TYPE_URL = "URL";
    private static final String SOURCE_TYPE_FILE = "FILE";

    private final VodJobRepository vodJobRepository;
    private final JobEventRepository jobEventRepository;

    public VodJobService(VodJobRepository vodJobRepository, JobEventRepository jobEventRepository) {
        this.vodJobRepository = vodJobRepository;
        this.jobEventRepository = jobEventRepository;
    }

    @Transactional
    public JobSummaryResponse createUrlJob(String url) {
        return createJob(SOURCE_TYPE_URL, url, null);
    }

    @Transactional
    public JobSummaryResponse createFileJob(String originalFilename) {
        return createJob(SOURCE_TYPE_FILE, null, originalFilename);
    }

    @Transactional(readOnly = true)
    public List<JobListItemResponse> listJobs() {
        return vodJobRepository.findAll(Sort.by(Sort.Direction.ASC, "createdAt", "id"))
                .stream()
                .map(JobMapper::toListItemResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public JobDetailResponse getJob(Long jobId) {
        VodJob job = vodJobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + jobId));
        return JobMapper.toDetailResponse(job);
    }

    @Transactional(readOnly = true)
    public List<JobEventResponse> listJobEvents(Long jobId) {
        getJob(jobId);
        return jobEventRepository.findAllByJobIdOrderByCreatedAtAscIdAsc(jobId)
                .stream()
                .map(event -> new JobEventResponse(
                        event.getId(),
                        event.getEventType(),
                        event.getMessage(),
                        event.getCreatedAt()
                ))
                .toList();
    }

    private JobSummaryResponse createJob(String sourceType, String sourceUrl, String originalFilename) {
        Instant now = Instant.now();

        VodJob job = new VodJob();
        job.setSourceType(sourceType);
        job.setSourceUrl(sourceUrl);
        job.setOriginalFilename(originalFilename);
        job.setStatus(JobStatus.NEW);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);

        VodJob savedJob = vodJobRepository.save(job);
        return JobMapper.toSummaryResponse(savedJob);
    }
}
