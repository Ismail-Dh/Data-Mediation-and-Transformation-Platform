/**
 * Emplacement conseillé : models/pipeline-provider-selection.ts
 * (à côté de models/pipeline.ts et models/provider.ts)
 *
 * Représente le choix d'un provider + de sa méthode HTTP pour UN pipeline.
 * La méthode HTTP se configure ici, à la création/édition du pipeline —
 * pas dans l'écran de gestion des providers — car un même provider peut
 * être appelé différemment (GET, POST, PUT, PATCH) selon le pipeline qui
 * l'utilise.
 *
 * Doit rester synchronisé avec com.miniESB.domain.enums.HttpRequestMethod
 * et com.miniESB.dto.Pipeline.PipelineProviderRequest côté backend.
 */
export type HttpRequestMethod = 'GET' | 'POST' | 'PUT' | 'PATCH';

export const HTTP_REQUEST_METHODS: HttpRequestMethod[] = ['GET', 'POST', 'PUT', 'PATCH'];

/** Envoyé dans CreatePipelineRequest.providers / UpdatePipelineRequest.providers */
export interface PipelineProviderSelection {
  providerId: number;
  httpMethod: HttpRequestMethod;
}

/**
 * NOTE d'intégration :
 * Le modèle `Provider` attaché à une réponse Pipeline (models/pipeline.ts,
 * champ `providers: Provider[]`) doit désormais exposer `httpMethod` en plus
 * de `id`, `name`, `endpoint` — puisque PipelineResponse.ProviderSummary
 * renvoie maintenant ce champ (spécifique à CE pipeline, donc à ne pas
 * confondre avec une éventuelle propriété globale sur le modèle Provider
 * de l'écran d'admin providers, qui n'en a pas).
 *
 * Exemple de mise à jour attendue dans models/pipeline.ts :
 *
 *   export interface Pipeline {
 *     ...
 *     providers: {
 *       id: number;
 *       name: string;
 *       endpoint: string;
 *       httpMethod: HttpRequestMethod; // ← nouveau champ
 *     }[];
 *   }
 */