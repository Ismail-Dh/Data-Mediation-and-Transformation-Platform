package com.miniESB.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "provider_responses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "raw_content", columnDefinition = "TEXT", nullable = false)
    private String rawContent;

    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "duration_ms")
    private Long durationMs;

    /**
     * Lien vers le payload d'origine.
     * unique=true a été retiré : un payload peut désormais avoir une
     * ProviderResponse par provider attaché au pipeline (multi-provider).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payload_id", nullable = false)
    private Payload payload;

    /** Provider qui a produit cette réponse — permet de distinguer les réponses entre elles. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private Provider provider;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "exchange_log_id", unique = true)
    private ExchangeLog exchangeLog;
}