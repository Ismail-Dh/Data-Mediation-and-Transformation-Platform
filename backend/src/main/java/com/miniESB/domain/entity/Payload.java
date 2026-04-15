package com.miniESB.domain.entity;

import com.miniESB.domain.enums.DataFormat;
import com.miniESB.domain.enums.PayloadStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payloads")
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

    @OneToOne(mappedBy = "payload", cascade = CascadeType.ALL, orphanRemoval = true)
    private ProviderResponse providerResponse;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "exchange_log_id", unique = true)
    private ExchangeLog exchangeLog;

    public Payload() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRawContent() { return rawContent; }
    public void setRawContent(String rawContent) { this.rawContent = rawContent; }

    public DataFormat getFormat() { return format; }
    public void setFormat(DataFormat format) { this.format = format; }

    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }

    public PayloadStatus getStatus() { return status; }
    public void setStatus(PayloadStatus status) { this.status = status; }

    public Pipeline getPipeline() { return pipeline; }
    public void setPipeline(Pipeline pipeline) { this.pipeline = pipeline; }

    public ProviderResponse getProviderResponse() { return providerResponse; }
    public void setProviderResponse(ProviderResponse providerResponse) { this.providerResponse = providerResponse; }

    public ExchangeLog getExchangeLog() { return exchangeLog; }
    public void setExchangeLog(ExchangeLog exchangeLog) { this.exchangeLog = exchangeLog; }
}