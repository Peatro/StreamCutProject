package com.peatroxd.streamcutproject.workertask;

import com.peatroxd.streamcutproject.workerexecution.WorkerTaskType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkerTaskRepository extends JpaRepository<WorkerTask, Long> {

    List<WorkerTask> findAllByVodJobIdAndProcessingVersionAndStatusIn(
            Long jobId,
            Long processingVersion,
            Collection<WorkerTaskStatus> statuses
    );

    Optional<WorkerTask> findFirstByVodJobIdAndProcessingVersionAndTaskTypeAndStatusOrderByIdAsc(
            Long jobId,
            Long processingVersion,
            WorkerTaskType taskType,
            WorkerTaskStatus status
    );

    Optional<WorkerTask> findFirstByVodJobIdAndProcessingVersionAndTaskTypeAndCandidateIdAndStatusOrderByIdAsc(
            Long jobId,
            Long processingVersion,
            WorkerTaskType taskType,
            Long candidateId,
            WorkerTaskStatus status
    );
}
