package com.peatroxd.streamcutproject.clipcandidate;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ClipCandidateRepository extends JpaRepository<ClipCandidate, Long> {

    @Query("""
            select c
            from ClipCandidate c
            where c.vodJob.id = :jobId
            order by c.score desc, c.startSec asc, c.id asc
            """)
    List<ClipCandidate> findAllByJobIdOrderByScoreDescStartSecAscIdAsc(@Param("jobId") Long jobId);

    @Modifying
    @Query("""
            delete
            from ClipCandidate c
            where c.vodJob.id = :jobId
            """)
    void deleteAllByJobId(@Param("jobId") Long jobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c
            from ClipCandidate c
            where c.exportStatus = :exportStatus
              and c.vodJob.currentWorkerId is null
            order by c.vodJob.updatedAt asc, c.id asc
            """)
    List<ClipCandidate> findPendingExportsForUpdate(
            @Param("exportStatus") ExportStatus exportStatus,
            Pageable pageable
    );

    boolean existsByVodJobIdAndExportStatus(Long jobId, ExportStatus exportStatus);

    List<ClipCandidate> findAllByVodJobIdAndExportStatus(Long jobId, ExportStatus exportStatus);
}
