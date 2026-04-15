package com.miniESB.domain.entity;

import com.miniESB.domain.enums.ImageStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "docker_images")
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
    private List<BuildLog> buildLogs = new ArrayList<>();

    public DockerImage() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getImageName() { return imageName; }
    public void setImageName(String imageName) { this.imageName = imageName; }

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }

    public ImageStatus getStatus() { return status; }
    public void setStatus(ImageStatus status) { this.status = status; }

    public LocalDateTime getBuiltAt() { return builtAt; }
    public void setBuiltAt(LocalDateTime builtAt) { this.builtAt = builtAt; }

    public Pipeline getPipeline() { return pipeline; }
    public void setPipeline(Pipeline pipeline) { this.pipeline = pipeline; }

    public Registry getRegistry() { return registry; }
    public void setRegistry(Registry registry) { this.registry = registry; }

    public List<BuildLog> getBuildLogs() { return buildLogs; }
    public void setBuildLogs(List<BuildLog> buildLogs) { this.buildLogs = buildLogs; }
}