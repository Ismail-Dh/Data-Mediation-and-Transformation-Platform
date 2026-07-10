export type PipelineStatus = 'DRAFT' | 'CONFIGURED' | 'VALIDATED';
export type DataFormat     = 'JSON'  | 'XML'  | 'CSV'  | 'PLAIN_TEXT';
export type HttpRequestMethod = 'GET' | 'POST' | 'PUT' | 'PATCH';

/** Résumé d'un provider tel que renvoyé dans PipelineResponse (multi-provider T5). */
export interface ProviderSummary {
  id:       number;
  name:     string;
  endpoint: string;
}

/**
 * DTO Pipeline retourné par le backend.
 * Depuis la migration V23, un pipeline peut avoir plusieurs providers.
 * Les champs providerId / providerName / providerEndpoint sont conservés
 * comme propriétés calculées pour ne pas casser les composants existants.
 */
export interface Pipeline {
  id:           number;
  name:         string;
  version:      string;
  createdAt:    string;
  inputFormat:  DataFormat;
  outputFormat: DataFormat;
  status:       PipelineStatus;
  createdBy:    string;

  /** Liste des providers attachés — remplace les anciens champs singuliers. */
  providers: {
        id: number;
       name: string;
        endpoint: string;
        httpMethod: HttpRequestMethod; // ← nouveau champ
     }[];
}

/**
 * Helper : retourne le premier provider d'un pipeline (rétro-compatibilité).
 * Utilise ces fonctions dans les templates existants au lieu des anciens champs.
 */
export function firstProvider(p: Pipeline): ProviderSummary | null {
  return p.providers?.length ? p.providers[0] : null;
}
export function providerNames(p: Pipeline): string {
  return p.providers?.map(pr => pr.name).join(', ') || '';
}

export interface CreatePipelineRequest {
  name:         string;
  version?:     string;
  inputFormat:  string;
  outputFormat: string;
  /** Multi-provider : liste des IDs. Remplace l'ancien champ providerId. */
  providerIds?: number[];
}

export interface UpdatePipelineRequest {
  name?:         string;
  version?:      string;
  inputFormat?:  string;
  outputFormat?: string;
  providerIds?:  number[];
}