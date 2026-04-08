package com.peatroxd.streamcutproject.workertask;

import com.peatroxd.streamcutproject.workerexecution.WorkerTaskType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkerTaskRepository extends JpaRepository<WorkerTask, Long> {

    Optional<WorkerTask> findFirstByTaskTypeAndStatusOrderByIdAsc(
            WorkerTaskType taskType,
            WorkerTaskStatus status
    );

    List<WorkerTask> findAllByStatusInOrderByIdAsc(
            Collection<WorkerTaskStatus> statuses
    );

    List<WorkerTask> findAllByStatusInAndLastHeartbeatAtBeforeOrderByIdAsc(
            Collection<WorkerTaskStatus> statuses,
            java.time.Instant lastHeartbeatBefore
    );

    List<WorkerTask> findAllByVodJobIdAndProcessingVersionAndStatusIn(
            Long jobId,
            Long processingVersion,
            Collection<WorkerTaskStatus> statuses
    );

    List<WorkerTask> findAllByVodJobIdOrderByCreatedAtAscIdAsc(Long jobId);

    List<WorkerTask> findAllByVodJobIdInOrderByVodJobIdAscIdAsc(Collection<Long> jobIds);

    Optional<WorkerTask> findFirstByVodJobIdOrderByIdDesc(Long jobId);

    Optional<WorkerTask> findFirstByVodJobIdAndProcessingVersionAndTaskTypeAndStatusInOrderByIdAsc(
            Long jobId,
            Long processingVersion,
            WorkerTaskType taskType,
            Collection<WorkerTaskStatus> statuses
    );

    Optional<WorkerTask> findFirstByVodJobIdAndProcessingVersionAndTaskTypeAndStatusOrderByIdAsc(
            Long jobId,
            Long processingVersion,
            WorkerTaskType taskType,
            WorkerTaskStatus status
    );

    Optional<WorkerTask> findFirstByVodJobIdAndProcessingVersionAndTaskTypeAndCandidateIdAndStatusInOrderByIdAsc(
            Long jobId,
            Long processingVersion,
            WorkerTaskType taskType,
            Long candidateId,
            Collection<WorkerTaskStatus> statuses
    );

    Optional<WorkerTask> findFirstByVodJobIdAndProcessingVersionAndTaskTypeAndCandidateIdAndStatusOrderByIdAsc(
            Long jobId,
            Long processingVersion,
            WorkerTaskType taskType,
            Long candidateId,
            WorkerTaskStatus status
    );
}
