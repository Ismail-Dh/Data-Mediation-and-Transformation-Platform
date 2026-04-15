package com.miniESB.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "build_logs")
public class BuildLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "log_content", columnDefinition = "TEXT", nullable = false)
    private String logContent;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "success", nullable = false)
    private boolean success;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "docker_image_id", nullable = false)
    private DockerImage dockerImage;

    public BuildLog() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLogContent() { return logContent; }
    public void setLogContent(String logContent) { this.logContent = logContent; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public DockerImage getDockerImage() { return dockerImage; }
    public void setDockerImage(DockerImage dockerImage) { this.dockerImage = dockerImage; }
}