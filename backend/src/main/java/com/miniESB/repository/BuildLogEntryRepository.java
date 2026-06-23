package com.miniESB.repository;

import com.miniESB.domain.entity.BuildLogEntry;
import com.miniESB.domain.enums.ImageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository JPA pour les entrées de log de build SSE (tâche 5.4).
 */
@Repository
public interface BuildLogEntryRepository extends JpaRepository<BuildLogEntry, Long> {

    /** Historique des builds d'un pipeline, du plus récent au plus ancien. */
    List<BuildLogEntry> findByPipelineIdOrderByStartTimeDesc(Long pipelineId);

    /** Dernière entrée d'un pipeline avec un statut donné. */
    List<BuildLogEntry> findByPipelineIdAndStatus(Long pipelineId, ImageStatus status);

}