package com.miniESB.repository;

import com.miniESB.domain.entity.PipelineField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PipelineFieldRepository extends JpaRepository<PipelineField, Long> {

    // tous les champs d'une pipeline
    List<PipelineField> findAllByPipelineId(Long pipelineId);

    // vérifier si un fieldPath existe déjà sur cette pipeline (éviter les doublons)
    boolean existsByPipelineIdAndFieldPath(Long pipelineId, String fieldPath);
}