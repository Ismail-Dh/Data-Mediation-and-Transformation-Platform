package com.miniESB.domain.entity;

import com.miniESB.domain.enums.RuleType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "validation_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "field_name", nullable = false, length = 150)
    private String fieldName;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 50)
    private RuleType ruleType;

    @Column(name = "pattern", length = 500)
    private String pattern;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "is_global", nullable = false)
    private boolean global;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_id", nullable = false)
    private Pipeline pipeline;
}