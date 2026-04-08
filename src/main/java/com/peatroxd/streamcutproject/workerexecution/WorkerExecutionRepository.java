package com.peatroxd.streamcutproject.workerexecution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

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
}
