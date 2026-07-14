package com.miniESB.engine.dispatch;

import com.miniESB.domain.enums.DataFormat;
import org.springframework.http.HttpMethod;

/**
 * Exécute un appel HTTP sortant vers un provider externe et classe le résultat
 * (succès / erreur HTTP / erreur réseau) en un {@link DispatchOutcome} neutre.
 *
 * <p>Avant ce refactoring, {@code ProviderDispatchServiceImpl} (mode base de
 * données) et {@code EngineProcessService} (mode fichier) dupliquaient
 * intégralement cette logique : construction du {@code RestTemplate}, des
 * en-têtes, et la même cascade {@code try/catch} sur
 * {@code HttpStatusCodeException}/{@code ResourceAccessException}/{@code Exception}
 * — violation SRP + DRY. Chaque appelant garde sa propre logique de
 * persistance ou de sérialisation, mais l'appel HTTP lui-même est partagé.</p>
 */
public interface HttpDispatchExecutor {

    DispatchOutcome call(String endpoint, HttpMethod method, String body,
                          DataFormat outputFormat, int timeoutSec, String providerLabel);
}
