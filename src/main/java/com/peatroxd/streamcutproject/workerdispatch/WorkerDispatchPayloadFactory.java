package com.peatroxd.streamcutproject.workerdispatch;

import com.peatroxd.streamcutproject.vodjob.VodJob;
import org.springframework.stereotype.Component;

@Component
public class WorkerDispatchPayloadFactory {

    public WorkerDispatchPayload fromJob(VodJob job) {
        String videoPath = job.getStorageVideoPath();
        if (videoPath == null || videoPath.isBlank()) {
            throw new IllegalStateException("Job " + job.getId() + " has no storage video path");
        }

        return new WorkerDispatchPayload(
                job.getId(),
                videoPath,
                job.getSourceType(),
                job.getSourceUrl()
        );
    }
}
