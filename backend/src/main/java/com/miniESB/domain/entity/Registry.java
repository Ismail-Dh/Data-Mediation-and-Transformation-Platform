package com.miniESB.domain.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;


public class Registry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
 
    @Column(name = "url", nullable = false, length = 500)
    private String url;
 
    @Column(name = "username", nullable = false, length = 100)
    private String username;
 
    @Column(name = "encrypted_password", nullable = false)
    private String encryptedPassword;
 
    @OneToMany(mappedBy = "registry")
    private List<DockerImage> dockerImages = new ArrayList<>();
 
    public Registry() {}
 
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
 
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
 
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
 
    public String getEncryptedPassword() { return encryptedPassword; }
    public void setEncryptedPassword(String encryptedPassword) { this.encryptedPassword = encryptedPassword; }
 
    public List<DockerImage> getDockerImages() { return dockerImages; }
    public void setDockerImages(List<DockerImage> dockerImages) { this.dockerImages = dockerImages; }
}
