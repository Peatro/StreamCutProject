package com.peatroxd.streamcutproject.clipcandidate;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
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

    Page<ClipCandidate> findAllByVodJobId(Long jobId, Pageable pageable);

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

    @Query("""
            select c.exportedClipPath
            from ClipCandidate c
            where c.vodJob.id = :jobId
              and c.exportStatus = :exportStatus
              and c.exportedClipPath is not null
            """)
    List<String> findExportedClipPaths(@Param("jobId") Long jobId, @Param("exportStatus") ExportStatus exportStatus);

    boolean existsByVodJobIdAndModerationStatus(Long jobId, ModerationStatus moderationStatus);

    @Query("""
            select case when count(c) > 0 then true else false end
            from ClipCandidate c
            where c.vodJob.id = :jobId
              and c.moderationStatus = com.peatroxd.streamcutproject.clipcandidate.ModerationStatus.APPROVED
              and c.exportStatus <> com.peatroxd.streamcutproject.clipcandidate.ExportStatus.COMPLETED
            """)
    boolean existsByVodJobIdAndApprovedButNotExported(@Param("jobId") Long jobId);
}
