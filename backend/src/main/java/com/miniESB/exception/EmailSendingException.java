package com.miniESB.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Levée lorsque l'envoi d'un email (ex : code de réinitialisation de mot de passe)
 * échoue côté fournisseur SMTP (Brevo, etc.).
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class EmailSendingException extends RuntimeException {
    public EmailSendingException(String message, Throwable cause) {
        super(message, cause);
    }
}