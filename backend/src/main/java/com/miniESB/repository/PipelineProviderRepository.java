package com.miniESB.repository;

import com.miniESB.domain.entity.PipelineProvider;
import com.miniESB.domain.entity.PipelineProviderId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository dédié à l'association pipeline↔provider (méthode HTTP par lien).
 *
 * On sauvegarde/supprime ces lignes explicitement via ce repository plutôt que
 * de dépendre uniquement du cascade JPA sur {@code Pipeline.pipelineProviders} :
 * avec une clé composite dérivée (@IdClass) et un parent en @GeneratedValue
 * IDENTITY, le cascade automatique est fragile (l'ID du pipeline n'existe pas
 * encore au moment du cascade côté création) — d'où le bug "http_method
 * toujours POST en base".
 */
@Repository
public interface PipelineProviderRepository extends JpaRepository<PipelineProvider, PipelineProviderId> {

    List<PipelineProvider> findByPipeline_Id(Long pipelineId);

    @Modifying
    @Query("DELETE FROM PipelineProvider pp WHERE pp.pipeline.id = :pipelineId")
    void deleteAllByPipelineId(@Param("pipelineId") Long pipelineId);
}