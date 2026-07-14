package com.miniESB.service;

import com.miniESB.dto.process.ProcessRequest;
import com.miniESB.dto.process.ProcessResponse;

/**
 * T7 — Orchestre le flux complet déclenché par POST /api/process :
 *
 * <pre>
 *   1. Réception + validation structurelle du payload  (PayloadService)
 *   2. Mapping du payload entrant                      (MappingService)
 *   3. Dispatch HTTP vers tous les providers           (ProviderDispatchService)
 *   4. Validation + mapping des réponses providers     (ResponseMappingExecutionService)
 *   5. Agrégation et construction de la réponse finale
 *   6. Persistance du ConsumerResponse                 (T5 traçabilité)
 * </pre>
 */
public interface ProcessOrchestrationService {

    ProcessResponse process(ProcessRequest request);
}