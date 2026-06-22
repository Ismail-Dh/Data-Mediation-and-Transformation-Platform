package com.miniESB.domain.entity;

import com.miniESB.domain.enums.ImageStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "docker_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DockerImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_name", nullable = false, length = 300)
    private String imageName;

    @Column(name = "tag", nullable = false, length = 100)
    private String tag;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ImageStatus status;

    @Column(name = "built_at")
    private LocalDateTime builtAt;

    // ── Semantic versioning ───────────────────────────────────────────────────

    /**
     * Patch auto-incrémenté à chaque build réussi.
     * Repart à 0 quand pipeline.version change.
     */
    @Column(name = "version_patch", nullable = false)
    @Builder.Default
    private int versionPatch = 0;

    /**
     * Dernière pipeline.version utilisée lors d'un build.
     * Permet de détecter un changement de version pipeline → reset patch.
     */
    @Column(name = "last_pipeline_version", length = 50)
    private String lastPipelineVersion;

    // ── Relations ─────────────────────────────────────────────────────────────

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_id", nullable = false, unique = true)
    private Pipeline pipeline;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registry_id")
    private Registry registry;

    @OneToMany(mappedBy = "dockerImage", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BuildLog> buildLogs = new ArrayList<>();

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Retourne le tag sémantique complet : "{pipeline.version}.{patch}"
     * Ex : "1.2.3"
     */
    public String getSemanticTag() {
        String pipelineVersion = pipeline != null && pipeline.getVersion() != null
                ? pipeline.getVersion()
                : "1.0";
        return pipelineVersion + "." + versionPatch;
    }
}