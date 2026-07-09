package com.miniESB.domain.entity;

import com.miniESB.domain.enums.HttpRequestMethod;
import jakarta.persistence.*;
import lombok.*;

/**
 * Association pipeline ↔ provider (table {@code pipeline_providers}).
 *
 * Porte la méthode HTTP (GET, POST, PUT, PATCH) à utiliser pour transmettre
 * le payload mappé à ce provider — configurée lors de la création/édition
 * du pipeline, PAS dans l'écran de gestion des providers. Un même provider
 * peut donc être appelé en GET par un pipeline et en PUT par un autre.
 *
 * Remplace l'ancien @ManyToMany simple entre Pipeline et Provider (V23),
 * qui ne permettait pas de porter de données supplémentaires sur le lien.
 */
@Entity
@Table(name = "pipeline_providers")
@IdClass(PipelineProviderId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineProvider {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_id")
    private Pipeline pipeline;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private Provider provider;

    /** Méthode HTTP utilisée par ce pipeline pour appeler ce provider. Défaut POST. */
    @Enumerated(EnumType.STRING)
    @Column(name = "http_method", nullable = false, length = 10)
    @Builder.Default
    private HttpRequestMethod httpMethod = HttpRequestMethod.POST;
}