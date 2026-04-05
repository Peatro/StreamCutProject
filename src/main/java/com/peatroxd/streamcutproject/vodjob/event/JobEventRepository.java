package com.peatroxd.streamcutproject.vodjob.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JobEventRepository extends JpaRepository<JobEvent, Long> {

    @Query("""
            select e
            from JobEvent e
            where e.vodJob.id = :jobId
            order by e.createdAt asc, e.id asc
            """)
    List<JobEvent> findAllByJobIdOrderByCreatedAtAscIdAsc(@Param("jobId") Long jobId);
}
