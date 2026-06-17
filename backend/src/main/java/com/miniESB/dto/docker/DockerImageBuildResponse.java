package com.miniESB.dto.docker;

// Réponse après lancement de la génération
public record DockerImageBuildResponse(
    Long imageId,
    String imageName,
    String tag,
    String status,
    String message
) {}