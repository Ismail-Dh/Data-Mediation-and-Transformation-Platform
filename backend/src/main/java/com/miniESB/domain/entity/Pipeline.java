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

    /**
     * Providers attachés à ce pipeline, via l'association {@link PipelineProvider}
     * qui porte la méthode HTTP (GET/POST/PUT/PATCH) choisie pour chacun.
     * LECTURE SEULE ici : la persistance (create/update/delete) des liens se fait
     * explicitement via {@code PipelineProviderRepository} dans PipelineServiceImpl
     * — pas de cascade JPA (fragile avec une clé composite dérivée + parent IDENTITY).
     */
    @OneToMany(mappedBy = "pipeline", fetch = FetchType.LAZY)
    @Builder.Default
    private List<PipelineProvider> pipelineProviders = new ArrayList<>();

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

    @OneToMany(mappedBy = "pipeline", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PipelineField> fields = new ArrayList<>();

    /**
     * Règles de validation/transformation appliquées aux réponses des providers (T6).
     * Distinctes de mappingRules, qui s'appliquent sur le payload ENTRANT.
     */
    @OneToMany(mappedBy = "pipeline", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ResponseMappingRule> responseMappingRules = new ArrayList<>();
}