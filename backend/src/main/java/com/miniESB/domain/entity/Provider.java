package com.miniESB.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "providers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Provider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "endpoint", nullable = false, length = 500)
    private String endpoint;

    @Column(name = "protocol", nullable = false, length = 50)
    private String protocol;

    @Column(name = "timeout", nullable = false)
    private int timeout;

    /** Pipelines auxquels ce provider est attaché (via l'association PipelineProvider, qui porte la méthode HTTP). */
    @OneToMany(mappedBy = "provider", fetch = FetchType.LAZY)
    @Builder.Default
    private List<PipelineProvider> pipelineLinks = new ArrayList<>();
}