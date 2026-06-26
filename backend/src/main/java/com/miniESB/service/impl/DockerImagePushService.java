// service/impl/DockerImagePushService.java
package com.miniESB.service.impl;

import com.miniESB.domain.entity.DockerImage;
import com.miniESB.domain.entity.Registry;
import com.miniESB.domain.enums.ImageStatus;
import com.miniESB.dto.docker.PushImageResponse;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.DockerImageRepository;
import com.miniESB.repository.RegistryRepository;
import com.miniESB.service.RegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)
public class DockerImagePushService {

    private final DockerImageRepository dockerImageRepository;
    private final RegistryRepository    registryRepository;
    private final RegistryService       registryService;

    // ── Push ──────────────────────────────────────────────────────────────────

    public PushImageResponse pushImage(Long pipelineId, Long registryId) throws Exception {

        // 1 — charger l'image
        DockerImage dockerImage = dockerImageRepository.findByPipelineId(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No image found for pipeline id=" + pipelineId));

        if (dockerImage.getStatus() != ImageStatus.SUCCESS) {
            throw new IllegalStateException(
                    "Image must be SUCCESS before pushing — current: "
                            + dockerImage.getStatus());
        }

        // 2 — charger le registry
        Registry registry = registryRepository.findById(registryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Registry not found: " + registryId));

        String password  = registryService.decryptPassword(registryId);
        String localTag  = dockerImage.getImageName() + ":" + dockerImage.getTag();
        String remoteTag = buildRemoteTag(registry, dockerImage);

        try {
            dockerLogin(registry.getUrl(), registry.getUsername(), password);
            dockerTag(localTag, remoteTag);
            dockerPush(remoteTag);

            // 3 — lier le registry à l'image en DB
            dockerImage.setRegistry(registry);
            dockerImageRepository.save(dockerImage);

            log.info("Push successful: {} → {}", localTag, remoteTag);

            return new PushImageResponse(
                    pipelineId, registryId, registry.getName(),
                    remoteTag, "SUCCESS",
                    "Image pushed successfully to " + registry.getName());

        } finally {
            dockerLogout(registry.getUrl());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildRemoteTag(Registry registry, DockerImage image) {
        String url = registry.getUrl();
        // Docker Hub — pas d'URL dans le tag
        if (url == null || url.isBlank()
                || url.contains("hub.docker.com")) {
            return registry.getUsername() + "/"
                    + image.getImageName() + ":" + image.getTag();
        }
        // Registry privé — URL/username/image:tag
        return url + "/" + registry.getUsername() + "/"
                + image.getImageName() + ":" + image.getTag();
    }

    private void dockerLogin(String url, String username, String password) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "login",
                url == null || url.contains("hub.docker.com") ? "https://index.docker.io/v1/" : url,
                "--username", username,
                "--password-stdin"
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        process.getOutputStream().write(password.getBytes());
        process.getOutputStream().close();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                log.info("[docker login] {}", line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException(
                    "docker login failed (exit=" + exitCode + "): " + output);
        }
        log.info("Docker login successful");
    }

    private void dockerTag(String localTag, String remoteTag) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("docker", "tag", localTag, remoteTag);
        pb.redirectErrorStream(true);
        int exitCode = pb.start().waitFor();
        if (exitCode != 0) {
            throw new RuntimeException(
                    "docker tag failed: " + localTag + " → " + remoteTag);
        }
        log.info("Docker tag: {} → {}", localTag, remoteTag);
    }

    private void dockerPush(String remoteTag) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("docker", "push", remoteTag);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[docker push] {}", line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException(
                    "docker push failed with exit code: " + exitCode);
        }
        log.info("Docker push successful: {}", remoteTag);
    }

    private void dockerLogout(String url) {
        try {
            new ProcessBuilder("docker", "logout",
                    url == null || url.contains("hub.docker.com")
                            ? "https://index.docker.io/v1/" : url)
                    .redirectErrorStream(true)
                    .start()
                    .waitFor();
            log.info("Docker logout done");
        } catch (Exception e) {
            log.warn("Docker logout failed: {}", e.getMessage());
        }
    }
}