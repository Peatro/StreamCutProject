package com.peatroxd.streamcutproject.clipcandidate;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
