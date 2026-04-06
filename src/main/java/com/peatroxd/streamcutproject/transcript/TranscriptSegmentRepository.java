package com.peatroxd.streamcutproject.transcript;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TranscriptSegmentRepository extends JpaRepository<TranscriptSegment, Long> {

    @Query("""
            select s
            from TranscriptSegment s
            where s.vodJob.id = :jobId
            order by s.startSec asc, s.id asc
            """)
    List<TranscriptSegment> findAllByJobIdOrderByStartSecAscIdAsc(@Param("jobId") Long jobId);

    @Modifying
    @Query("""
            delete
            from TranscriptSegment s
            where s.vodJob.id = :jobId
            """)
    void deleteAllByJobId(@Param("jobId") Long jobId);
}
