package com.miniESB.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Levée quand le daemon Docker est accessible mais que {@code docker build} échoue
 * (exit code != 0, erreur Dockerfile, layer manquant, etc.).
 *
 * <p>Garantit que :
 * <ul>
 *   <li>Le pipeline conserve son statut {@code VALIDATED} — seul le {@link com.miniESB.domain.entity.DockerImage}
 *       passe en {@code FAILED}.</li>
 *   <li>Le log complet du build est exposé dans la réponse pour permettre au développeur
 *       de diagnostiquer l'erreur sans accéder aux logs serveur.</li>
 * </ul>
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class DockerBuildException extends RuntimeException {

    /** Exit code retourné par {@code docker build}. */
    private final int exitCode;

    /** Log complet (stdout+stderr) du processus {@code docker build}. */
    private final String buildLog;

    public DockerBuildException(String message, int exitCode, String buildLog) {
        super(message);
        this.exitCode = exitCode;
        this.buildLog = buildLog;
    }

    public int getExitCode() {
        return exitCode;
    }

    public String getBuildLog() {
        return buildLog;
    }
}