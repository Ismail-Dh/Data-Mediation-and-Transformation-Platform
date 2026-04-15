package com.miniESB.domain.entity;

import com.miniESB.domain.enums.DataFormat;
import com.miniESB.domain.enums.PipelineStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pipelines")
public class Pipeline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "provider_url", length = 500)
    private String providerUrl;
    
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
    @JoinColumn(name = "consumer_id", nullable = false)
    private Consumer consumer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @OneToMany(mappedBy = "pipeline", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ValidationRule> validationRules = new ArrayList<>();

    @OneToMany(mappedBy = "pipeline", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MappingRule> mappingRules = new ArrayList<>();

    @OneToMany(mappedBy = "pipeline", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Payload> payloads = new ArrayList<>();

    @OneToMany(mappedBy = "pipeline", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExchangeLog> exchangeLogs = new ArrayList<>();

    @OneToOne(mappedBy = "pipeline", cascade = CascadeType.ALL, orphanRemoval = true)
    private DockerImage dockerImage;

    public Pipeline() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProviderUrl() { return providerUrl; }
    public void setProviderUrl(String providerUrl) { this.providerUrl = providerUrl; }

    public DataFormat getInputFormat() { return inputFormat; }
    public void setInputFormat(DataFormat inputFormat) { this.inputFormat = inputFormat; }

    public DataFormat getOutputFormat() { return outputFormat; }
    public void setOutputFormat(DataFormat outputFormat) { this.outputFormat = outputFormat; }

    public PipelineStatus getStatus() { return status; }
    public void setStatus(PipelineStatus status) { this.status = status; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }

    public Consumer getConsumer() { return consumer; }
    public void setConsumer(Consumer consumer) { this.consumer = consumer; }

    public Provider getProvider() { return provider; }
    public void setProvider(Provider provider) { this.provider = provider; }

    public List<ValidationRule> getValidationRules() { return validationRules; }
    public void setValidationRules(List<ValidationRule> validationRules) { this.validationRules = validationRules; }

    public List<MappingRule> getMappingRules() { return mappingRules; }
    public void setMappingRules(List<MappingRule> mappingRules) { this.mappingRules = mappingRules; }

    public List<Payload> getPayloads() { return payloads; }
    public void setPayloads(List<Payload> payloads) { this.payloads = payloads; }

    public List<ExchangeLog> getExchangeLogs() { return exchangeLogs; }
    public void setExchangeLogs(List<ExchangeLog> exchangeLogs) { this.exchangeLogs = exchangeLogs; }

    public DockerImage getDockerImage() { return dockerImage; }
    public void setDockerImage(DockerImage dockerImage) { this.dockerImage = dockerImage; }
}