package com.miniESB.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sandbox_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SandboxLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_id", nullable = false)
    private Pipeline pipeline;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payload_id")
    private Payload payload;

    @Column(name = "validation_passed", nullable = false)
    private boolean validationPassed;

    @Column(name = "mapping_applied", nullable = false)
    private boolean mappingApplied;

    @Column(name = "validation_message", columnDefinition = "TEXT")
    private String validationMessage;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    @Column(name = "input_format", length = 20)
    private String inputFormat;

    @Column(name = "raw_content", columnDefinition = "TEXT")
    private String rawContent;

    @Column(name = "failure_step", length = 20)
    private String failureStep;

    @Column(name = "violations", columnDefinition = "TEXT")
    private String violations;

    @Column(name = "original_payload", columnDefinition = "TEXT")
    private String originalPayload;

    @Column(name = "mapped_payload", columnDefinition = "TEXT")
    private String mappedPayload;

    @Column(name = "mapping_summary", columnDefinition = "TEXT")
    private String mappingSummary;
}