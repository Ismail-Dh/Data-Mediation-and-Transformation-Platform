package com.miniESB.service;

import com.miniESB.domain.entity.DockerImage;
import com.miniESB.domain.entity.Pipeline;
import com.miniESB.dto.docker.VersionBumpRequest;
import com.miniESB.dto.docker.VersionBumpResponse;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.DockerImageRepository;
import com.miniESB.repository.PipelineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageVersionService {

    private final DockerImageRepository dockerImageRepository;
    private final PipelineRepository    pipelineRepository;

    // ══════════════════════════════════════════════════════════════════════════
    //  Calcul automatique du prochain tag (appelé avant chaque build)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Calcule le prochain tag sémantique pour l'image d'un pipeline.
     *
     * <p>Règles :
     * <ul>
     *   <li>Si c'est le premier build → tag = "{pipeline.version}.0"</li>
     *   <li>Si {@code pipeline.version} a changé depuis le dernier build
     *       → patch repart à 0 : "{pipeline.version}.0"</li>
     *   <li>Sinon → patch incrémenté : "{pipeline.version}.{patch+1}"</li>
     * </ul>
     *
     * <p><strong>Attention</strong> : cette méthode ne persiste pas encore les champs —
     * elle retourne seulement le tag calculé. C'est {@link #applyNextVersion}
     * qui met à jour l'entité {@link DockerImage} après un build réussi.
     *
     * @param dockerImage  l'entité DockerImage existante ou nouvellement créée
     * @param pipeline     le pipeline associé
     * @return le tag calculé, ex : "1.2.3"
     */
    public String computeNextTag(DockerImage dockerImage, Pipeline pipeline) {
        String currentPipelineVersion = pipeline.getVersion() != null
                ? pipeline.getVersion()
                : "1.0";

        boolean isFirstBuild     = dockerImage.getLastPipelineVersion() == null;
        boolean pipelineVersionChanged = !isFirstBuild
                && !currentPipelineVersion.equals(dockerImage.getLastPipelineVersion());

        int nextPatch;
        if (isFirstBuild || pipelineVersionChanged) {
            nextPatch = 0;
            log.info("Version reset: pipeline={} pipelineVersion={} (first={}, changed={})",
                    pipeline.getId(), currentPipelineVersion, isFirstBuild, pipelineVersionChanged);
        } else {
            nextPatch = dockerImage.getVersionPatch() + 1;
            log.info("Version patch increment: pipeline={} pipelineVersion={} patch={}→{}",
                    pipeline.getId(), currentPipelineVersion,
                    dockerImage.getVersionPatch(), nextPatch);
        }

        return currentPipelineVersion + "." + nextPatch;
    }

    /**
     * Applique le tag calculé sur l'entité {@link DockerImage} après un build réussi.
     * Met à jour {@code versionPatch} et {@code lastPipelineVersion}.
     *
     * <p>À appeler dans {@code DockerImageGeneratorService} juste avant
     * {@code dockerImageRepository.save(dockerImage)} en cas de succès.
     *
     * @param dockerImage           l'entité à mettre à jour
     * @param appliedTag            le tag retourné par {@link #computeNextTag}
     * @param currentPipelineVersion la version pipeline utilisée pour ce build
     */
    public void applyNextVersion(DockerImage dockerImage,
                                  String appliedTag,
                                  String currentPipelineVersion) {
        // Extraire le patch depuis le tag "X.Y.Z" → Z
        String[] parts = appliedTag.split("\\.");
        int patch = Integer.parseInt(parts[parts.length - 1]);

        dockerImage.setVersionPatch(patch);
        dockerImage.setLastPipelineVersion(currentPipelineVersion);
        dockerImage.setTag(appliedTag);

        log.info("Applied semantic version: tag={} patch={} lastPipelineVersion={}",
                appliedTag, patch, currentPipelineVersion);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Bump manuel MINOR / MAJOR → met à jour pipeline.version
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Bump manuel de version (MINOR ou MAJOR) sur le pipeline associé à une image.
     *
     * <p>MINOR : "1.2" → "1.3"
     * <p>MAJOR : "1.2" → "2.0"
     *
     * <p>Le patch sera remis à 0 automatiquement au prochain build
     * (car {@code pipeline.version} aura changé).
     *
     * @param pipelineId identifiant du pipeline
     * @param request    type de bump + version cible optionnelle
     * @return réponse avec l'ancienne et la nouvelle version
     */
    @Transactional
    public VersionBumpResponse bumpVersion(Long pipelineId, VersionBumpRequest request) {

        Pipeline pipeline = pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pipeline not found with id=" + pipelineId));

        String currentVersion = pipeline.getVersion() != null
                ? pipeline.getVersion()
                : "1.0";

        String newVersion;

        // Version cible fournie explicitement → on l'utilise directement
        if (request.targetVersion() != null && !request.targetVersion().isBlank()) {
            newVersion = request.targetVersion();
        } else {
            newVersion = computeBumpedVersion(currentVersion, request.type());
        }

        pipeline.setVersion(newVersion);
        pipelineRepository.save(pipeline);

        log.info("Version bumped: pipeline={} {} → {} (type={})",
                pipelineId, currentVersion, newVersion, request.type());

        return new VersionBumpResponse(
                pipelineId,
                currentVersion,
                newVersion,
                "Version bumped to " + newVersion
                        + ". Patch will reset to 0 on next build."
        );
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Private helpers
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Calcule la nouvelle version pipeline selon le type de bump.
     *
     * <p>Format attendu de {@code currentVersion} : "major.minor" (ex : "1.2").
     * Si le format est invalide, repart de "1.0".
     */
    private String computeBumpedVersion(String currentVersion,
                                         VersionBumpRequest.BumpType type) {
        int major = 1;
        int minor = 0;

        try {
            String[] parts = currentVersion.split("\\.");
            major = Integer.parseInt(parts[0]);
            minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        } catch (NumberFormatException e) {
            log.warn("Invalid pipeline version format '{}', resetting to 1.0", currentVersion);
        }

        return switch (type) {
            case MINOR -> major + "." + (minor + 1);
            case MAJOR -> (major + 1) + ".0";
        };
    }
}