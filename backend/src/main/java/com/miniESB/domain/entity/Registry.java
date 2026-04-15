package com.miniESB.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "registries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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
    @Builder.Default
    private List<DockerImage> dockerImages = new ArrayList<>();
}