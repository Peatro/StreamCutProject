package com.peatroxd.streamcutproject.vodjob;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface VodJobRepository extends JpaRepository<VodJob, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select j
            from VodJob j
            where j.status = :status
            order by j.createdAt asc, j.id asc
            """)
    List<VodJob> findAllByStatusForUpdate(@Param("status") JobStatus status, Pageable pageable);
}
