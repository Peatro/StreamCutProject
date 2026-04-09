package com.peatroxd.streamcutproject.workerexecution;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    Optional<WorkerExecution> findFirstByWorkerTaskIdOrderByIdDesc(Long workerTaskId);

    Optional<WorkerExecution> findById(Long id);

    @Modifying
    @Query("""
            delete
            from WorkerExecution e
            where e.vodJob.id = :jobId
            """)
    void deleteAllByVodJobId(@Param("jobId") Long jobId);
}
