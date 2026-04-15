package com.miniESB.domain.entity;

import com.miniESB.domain.enums.MappingType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "mapping_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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
}