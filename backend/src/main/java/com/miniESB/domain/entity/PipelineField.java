package com.miniESB.domain.entity;

import com.miniESB.domain.enums.FieldType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "pipeline_fields")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "field_path", nullable = false, length = 200)
    private String fieldPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 30)
    private FieldType fieldType;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "nullable", nullable = false)
    private boolean nullable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_id", nullable = false)
    private Pipeline pipeline;
}