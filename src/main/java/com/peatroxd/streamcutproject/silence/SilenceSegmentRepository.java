package com.peatroxd.streamcutproject.silence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SilenceSegmentRepository extends JpaRepository<SilenceSegment, Long> {

    @Query("""
            select s
            from SilenceSegment s
            where s.vodJob.id = :jobId
            order by s.startSec asc, s.id asc
            """)
    List<SilenceSegment> findAllByJobIdOrderByStartSecAscIdAsc(@Param("jobId") Long jobId);
}
