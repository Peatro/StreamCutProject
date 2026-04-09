package com.peatroxd.streamcutproject.vodjob;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.time.Instant;
import java.util.List;

public interface VodJobRepository extends JpaRepository<VodJob, Long> {

    long countByStatus(JobStatus status);

    long countByStatusIn(Collection<JobStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select j
            from VodJob j
            where j.status = :status
            order by j.createdAt asc, j.id asc
            """)
    List<VodJob> findAllByStatusForUpdate(@Param("status") JobStatus status, Pageable pageable);

    @Query("""
            select j
            from VodJob j
            where j.status in :statuses
              and j.storageVideoPath is not null
              and trim(j.storageVideoPath) <> ''
              and coalesce(j.finishedAt, j.updatedAt) < :retainedBefore
            order by coalesce(j.finishedAt, j.updatedAt) asc, j.id asc
            """)
    List<VodJob> findAllByStatusInAndExpiredSourceRetention(
            @Param("statuses") Collection<JobStatus> statuses,
            @Param("retainedBefore") Instant retainedBefore
    );

    @Query("""
            select j
            from VodJob j
            where j.status = :status
              and coalesce(j.finishedAt, j.updatedAt) < :retainedBefore
            order by coalesce(j.finishedAt, j.updatedAt) asc, j.id asc
            """)
    List<VodJob> findAllByStatusAndExpiredRetention(
            @Param("status") JobStatus status,
            @Param("retainedBefore") Instant retainedBefore
    );
}
