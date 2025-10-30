package com.jieun.lyrimood.application.port;

import com.jieun.lyrimood.domain.model.AnalysisLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalysisLogRepository extends JpaRepository<AnalysisLog, Long> {

    List<AnalysisLog> findTop20ByOrderByCreatedAtDesc();
}

