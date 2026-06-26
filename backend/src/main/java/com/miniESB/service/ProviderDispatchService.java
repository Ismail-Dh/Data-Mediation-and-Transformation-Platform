package com.miniESB.service;

import com.miniESB.dto.dispatch.ProviderDispatchResult;

import java.util.List;

/**
 * Envoie le payload mappé à chaque provider attaché au pipeline.
 *
 * <p>Un pipeline possède actuellement un seul provider (relation @ManyToOne),
 * mais l'interface retourne une liste pour anticiper l'évolution vers
 * plusieurs providers par pipeline sans modifier les appelants.</p>
 */
public interface ProviderDispatchService {

    /**
     * Dispatche {@code mappedPayload} vers tous les providers du pipeline {@code pipelineId}.
     *
     * @param pipelineId   identifiant du pipeline source
     * @param payloadId    identifiant du payload traité (pour persistance de la réponse)
     * @param mappedPayload contenu mappé à envoyer (JSON, XML… selon outputFormat du pipeline)
     * @return une liste de résultats, un par provider contacté
     */
    List<ProviderDispatchResult> dispatch(Long pipelineId, Long payloadId, String mappedPayload);
}