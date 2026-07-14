package com.miniESB.engine.dispatch;

/**
 * Résultat neutre d'un appel HTTP sortant vers un provider, indépendant de ce
 * que l'appelant en fait ensuite (persistance JPA côté {@code ProviderDispatchServiceImpl},
 * ou simple entrée de Map JSON côté {@code EngineProcessService} en mode fichier).
 */
public record DispatchOutcome(
        int httpStatus,
        boolean success,
        String rawBody,
        long durationMs,
        String errorMessage
) {
    public static DispatchOutcome success(int httpStatus, String rawBody, long durationMs) {
        return new DispatchOutcome(httpStatus, true, rawBody, durationMs, null);
    }

    public static DispatchOutcome httpError(int httpStatus, String rawBody, long durationMs) {
        return new DispatchOutcome(httpStatus, false, rawBody, durationMs, null);
    }

    public static DispatchOutcome networkError(long durationMs, String errorMessage) {
        return new DispatchOutcome(0, false, null, durationMs, errorMessage);
    }
}
