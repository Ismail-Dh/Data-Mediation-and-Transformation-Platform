package com.miniESB.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Levée quand le daemon Docker est inaccessible (socket absent, service arrêté, etc.).
 *
 * <p>Garantit que :
 * <ul>
 *   <li>Le pipeline conserve son statut {@code VALIDATED} — aucun build n'a été tenté.</li>
 *   <li>Le GlobalExceptionHandler répond HTTP 503 avec un corps détaillé.</li>
 * </ul>
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class DockerDaemonException extends RuntimeException {

    /** Cause technique (message de l'IOException sous-jacente). */
    private final String technicalDetail;

    public DockerDaemonException(String message, String technicalDetail) {
        super(message);
        this.technicalDetail = technicalDetail;
    }

    public DockerDaemonException(String message, Throwable cause) {
        super(message, cause);
        this.technicalDetail = cause != null ? cause.getMessage() : null;
    }

    public String getTechnicalDetail() {
        return technicalDetail;
    }
}