package com.miniESB.domain.entity;

import com.miniESB.domain.enums.DataFormat;
import com.miniESB.domain.enums.PipelineStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pipelines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pipeline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;


    @Column(name = "version", length = 50)
    private String version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "input_format", nullable = false, length = 20)
    private DataFormat inputFormat;

    @Enumerated(EnumType.STRING)
    @Column(name = "output_format", nullable = false, length = 20)
    private DataFormat outputFormat;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PipelineStatus status;

    // --- Relations ---

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = true)
    private Provider provider;

    @OneToMany(mappedBy = "pipeline",cascade = CascadeType.ALL,orphanRemoval = true)
    @Builder.Default
    private List<ValidationRule> validationRules = new ArrayList<>();

    @OneToMany(mappedBy = "pipeline", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MappingRule> mappingRules = new ArrayList<>();

    @OneToMany(mappedBy = "pipeline", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Payload> payloads = new ArrayList<>();

    @OneToMany(mappedBy = "pipeline", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ExchangeLog> exchangeLogs = new ArrayList<>();

    @OneToOne(mappedBy = "pipeline", cascade = CascadeType.ALL, orphanRemoval = true)
    private DockerImage dockerImage;

    // --- Nouvelles liaisons vers les templates (nullable — optionnels) ---

    /**
     * Template de validation attaché à ce pipeline (optionnel).
     * Seuls les templates PUBLISHED sont attachables.
     * Au runtime, ses règles sont appliquées EN PREMIER sur le payload entrant,
     * avant les GlobalValidationRules et les ValidationRules privées.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validation_template_id", nullable = true)
    private ValidationTemplate validationTemplate;

    /**
     * Template de mapping attaché à ce pipeline (optionnel).
     * Seuls les templates PUBLISHED sont attachables.
     * Au runtime, ses mappings sont appliqués EN PREMIER sur le payload validé,
     * avant les MappingRules privées du pipeline.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mapping_template_id", nullable = true)
    private MappingTemplate mappingTemplate;
    @OneToMany(mappedBy = "pipeline", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PipelineField> fields = new ArrayList<>();
}