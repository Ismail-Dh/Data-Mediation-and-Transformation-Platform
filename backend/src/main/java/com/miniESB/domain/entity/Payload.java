package com.miniESB.domain.entity;

import com.miniESB.domain.enums.DataFormat;
import com.miniESB.domain.enums.PayloadStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payloads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "raw_content", columnDefinition = "TEXT", nullable = false)
    private String rawContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, length = 20)
    private DataFormat format;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PayloadStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_id", nullable = false)
    private Pipeline pipeline;

    /**
     * Réponses des providers pour ce payload.
     * Migré de @OneToOne vers @OneToMany : un payload peut désormais être
     * dispatché vers plusieurs providers, chacun produisant sa propre réponse.
     */
    @OneToMany(mappedBy = "payload", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private java.util.List<ProviderResponse> providerResponses = new java.util.ArrayList<>();

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "exchange_log_id", unique = true)
    private ExchangeLog exchangeLog;
}