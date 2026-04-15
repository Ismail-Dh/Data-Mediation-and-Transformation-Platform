package com.miniESB.domain.entity;

import com.miniESB.domain.enums.ImageStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "docker_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DockerImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_name", nullable = false, length = 300)
    private String imageName;

    @Column(name = "tag", nullable = false, length = 100)
    private String tag;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ImageStatus status;

    @Column(name = "built_at")
    private LocalDateTime builtAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_id", nullable = false, unique = true)
    private Pipeline pipeline;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registry_id")
    private Registry registry;

    @OneToMany(mappedBy = "dockerImage", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BuildLog> buildLogs = new ArrayList<>();
}