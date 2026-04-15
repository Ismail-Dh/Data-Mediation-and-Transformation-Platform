package com.miniESB.domain.entity;

import com.miniESB.domain.enums.MappingType;
import jakarta.persistence.*;

@Entity
@Table(name = "mapping_rules")
public class MappingRule {

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

    @Column(name = "active", nullable = false)
    private boolean active;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_id", nullable = false)
    private Pipeline pipeline;

    public MappingRule() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSourceField() { return sourceField; }
    public void setSourceField(String sourceField) { this.sourceField = sourceField; }

    public String getTargetField() { return targetField; }
    public void setTargetField(String targetField) { this.targetField = targetField; }

    public MappingType getMappingType() { return mappingType; }
    public void setMappingType(MappingType mappingType) { this.mappingType = mappingType; }

    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Pipeline getPipeline() { return pipeline; }
    public void setPipeline(Pipeline pipeline) { this.pipeline = pipeline; }
}