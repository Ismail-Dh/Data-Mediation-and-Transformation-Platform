package com.miniESB.repository;

import com.miniESB.domain.entity.ExchangeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExchangeLogRepository extends JpaRepository<ExchangeLog, Long> {

    /** Retrouve tous les échanges d'un pipeline, du plus récent au plus ancien. */
    List<ExchangeLog> findAllByPipelineIdOrderByTimestampDesc(Long pipelineId);
}