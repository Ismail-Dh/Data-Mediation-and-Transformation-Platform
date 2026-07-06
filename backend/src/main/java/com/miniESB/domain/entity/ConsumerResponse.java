package com.miniESB.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Résultat agrégé final construit pour le consommateur après le flux complet
 * {@code /api/process} (validation → mapping → dispatch multi-provider →
 * validation/mapping des réponses → agrégation).
 *
 * <p>Persisté pour traçabilité et pour que le frontend (T10) affiche
 * l'historique des exécutions sans avoir à recombiner les ProviderResponse
 * et ExchangeLog à chaque consultation.</p>
 */
@Entity
@Table(name = "consumer_responses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsumerResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payload_id", nullable = false)
    private Payload payload;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_id", nullable = false)
    private Pipeline pipeline;

    @Column(name = "built_at", nullable = false)
    private LocalDateTime builtAt;

    /** Corps JSON agrégé final, tel que renvoyé au consommateur. */
    @Column(name = "aggregated_body", columnDefinition = "TEXT", nullable = false)
    private String aggregatedBody;

    @Column(name = "overall_success", nullable = false)
    private boolean overallSuccess;

    @Column(name = "provider_count", nullable = false)
    private int providerCount;

    @Column(name = "success_count", nullable = false)
    private int successCount;
}