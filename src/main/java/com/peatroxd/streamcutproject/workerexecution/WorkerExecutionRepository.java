package com.peatroxd.streamcutproject.workerexecution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface WorkerExecutionRepository extends JpaRepository<WorkerExecution, Long> {

    Optional<WorkerExecution> findFirstByVodJobIdAndProcessingVersionAndWorkerIdAndStatusInOrderByIdDesc(
            Long jobId,
            Long processingVersion,
            String workerId,
            Collection<WorkerExecutionStatus> statuses
    );

    List<WorkerExecution> findAllByVodJobIdAndProcessingVersionAndStatusIn(
            Long jobId,
            Long processingVersion,
            Collection<WorkerExecutionStatus> statuses
    );

    List<WorkerExecution> findAllByVodJobIdOrderByClaimedAtAscIdAsc(Long jobId);

    List<WorkerExecution> findAllByVodJobIdInOrderByVodJobIdAscIdAsc(Collection<Long> jobIds);

    List<WorkerExecution> findAllByStatusInAndLastHeartbeatAtBeforeOrderByIdAsc(
            Collection<WorkerExecutionStatus> statuses,
            Instant lastHeartbeatBefore
    );

    Optional<WorkerExecution> findFirstByVodJobIdOrderByIdDesc(Long jobId);

    Optional<WorkerExecution> findById(Long id);
}
