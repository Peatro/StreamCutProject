package com.peatroxd.streamcutproject.analysiswindow;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AnalysisWindowRepository extends JpaRepository<AnalysisWindow, Long> {

    @Query("""
            select w
            from AnalysisWindow w
            where w.vodJob.id = :jobId
            order by w.startSec asc, w.id asc
            """)
    List<AnalysisWindow> findAllByJobIdOrderByStartSecAscIdAsc(@Param("jobId") Long jobId);

    @Modifying
    @Query("""
            delete
            from AnalysisWindow w
            where w.vodJob.id = :jobId
            """)
    void deleteAllByJobId(@Param("jobId") Long jobId);
}
