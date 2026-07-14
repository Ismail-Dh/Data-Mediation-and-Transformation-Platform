package com.miniESB.domain.entity;

import com.miniESB.domain.enums.MappingType;
import jakarta.persistence.*;
import lombok.*;

/**
 * Règle de validation/transformation appliquée à la RÉPONSE d'un provider,
 * par opposition à {@link MappingRule} qui s'applique au payload ENTRANT.
 *
 * <p>Si {@code provider} est null, la règle s'applique à toutes les réponses
 * du pipeline, quel que soit le provider qui a répondu. Si {@code provider}
 * est renseigné, la règle ne s'applique qu'aux réponses de ce provider précis —
 * utile quand deux providers renvoient des formats différents pour le même pipeline.</p>
 *
 * <p>{@code required=true} signifie que si le champ {@code sourceField} est absent
 * de la réponse du provider, la validation échoue (voir ResponseMappingExecutionService).</p>
 */
@Entity
@Table(name = "response_mapping_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResponseMappingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_field", nullable = false, length = 200)
    private String sourceField;

    @Column(name = "target_field", nullable = false, length = 200)
    private String targetField;

    @Enumerated(EnumType.STRING)
    @Column(name = "mapping_type", nullable = false, length = 50)
    private MappingType mappingType;

    @Column(name = "expression", columnDefinition = "TEXT")
    private String expression;

    /** Si true, le champ source doit être présent dans la réponse du provider, sinon échec de validation. */
    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "active", nullable = false)
    private boolean active;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_id", nullable = false)
    private Pipeline pipeline;

    /** Provider ciblé (optionnel) — null = s'applique à tous les providers du pipeline. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private Provider provider;
}