package com.miniESB.dto.dispatch;

/**
 * Résultat du dispatch HTTP vers un provider externe.
 * Retourné par ProviderDispatchService pour chaque provider appelé.
 *
 * @param providerId   ID du provider ciblé
 * @param providerName Nom lisible du provider
 * @param endpoint     URL appelée
 * @param httpStatus   Code HTTP reçu (0 si timeout / erreur réseau)
 * @param rawBody      Corps brut de la réponse (peut être null en cas d'erreur réseau)
 * @param durationMs   Durée de l'appel en millisecondes
 * @param success      true si httpStatus est 2xx
 * @param errorMessage Message d'erreur si échec réseau (null si success ou erreur HTTP)
 */
public record ProviderDispatchResult(
        Long   providerId,
        String providerName,
        String endpoint,
        int    httpStatus,
        String rawBody,
        long   durationMs,
        boolean success,
        String errorMessage
) {
    /** Construit un résultat de succès (2xx). */
    public static ProviderDispatchResult ok(Long providerId, String providerName,
                                            String endpoint, int httpStatus,
                                            String rawBody, long durationMs) {
        return new ProviderDispatchResult(
                providerId, providerName, endpoint,
                httpStatus, rawBody, durationMs, true, null);
    }

    /** Construit un résultat d'échec HTTP (4xx / 5xx). */
    public static ProviderDispatchResult httpError(Long providerId, String providerName,
                                                   String endpoint, int httpStatus,
                                                   String rawBody, long durationMs) {
        return new ProviderDispatchResult(
                providerId, providerName, endpoint,
                httpStatus, rawBody, durationMs, false, null);
    }

    /** Construit un résultat d'échec réseau (timeout, DNS, connexion refusée…). */
    public static ProviderDispatchResult networkError(Long providerId, String providerName,
                                                      String endpoint, long durationMs,
                                                      String errorMessage) {
        return new ProviderDispatchResult(
                providerId, providerName, endpoint,
                0, null, durationMs, false, errorMessage);
    }
}