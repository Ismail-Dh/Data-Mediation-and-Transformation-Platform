package com.miniESB.domain.entity;

import com.miniESB.domain.enums.ImageStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entité dédiée au streaming SSE des builds Docker (tâche 5.4).
 * Distincte de BuildLog (existant) pour ne pas casser le schéma existant.
 */
@Entity
@Table(name = "build_log_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BuildLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID du pipeline qui a déclenché le build. */
    @Column(name = "pipeline_id", nullable = false)
    private Long pipelineId;

    /** Version du pipeline au moment du build (ex : "1.0.0"). */
    @Column(name = "version", length = 50)
    private String version;

    /** Log complet accumulé ligne par ligne pendant le docker build. */
    @Column(name = "full_log", columnDefinition = "TEXT")
    private String fullLog;

    /** Résultat final du build : SUCCESS ou FAILED. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ImageStatus status;

    /** Horodatage de début du build. */
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    /** Horodatage de fin du build (null si en cours). */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /** Image Docker produite (ou tentée) par ce build. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "docker_image_id", nullable = false)
    private DockerImage dockerImage;
}