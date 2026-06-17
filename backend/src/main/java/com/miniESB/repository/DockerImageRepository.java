package com.miniESB.repository;

import com.miniESB.domain.entity.DockerImage;
import com.miniESB.domain.enums.ImageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DockerImageRepository extends JpaRepository<DockerImage, Long> {

    // Récupère l'image d'un pipeline
    Optional<DockerImage> findByPipelineId(Long pipelineId);

    // Vérifie si une image existe déjà pour ce pipeline
    boolean existsByPipelineId(Long pipelineId);

    // Récupère les images par statut
    java.util.List<DockerImage> findAllByStatus(ImageStatus status);
}