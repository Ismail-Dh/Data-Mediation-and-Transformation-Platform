// ── /api/process ──────────────────────────────────────────────────────────────

export interface ProcessRequest {
  pipelineId:   number;
  rawContent:   string;
  inputFormat?: string;
}

export interface ProviderResponseDetail {
  providerId:       number;
  providerName:     string;
  httpStatus:       number;
  rawBody:          string | null;
  durationMs:       number;
  dispatchSuccess:  boolean;
  receivedAt:       string;
  validationPassed: boolean;
  validationErrors: string[];
  mappedBody:       Record<string, unknown>;
}

export interface ProcessResponse {
  payloadId:       number;
  pipelineId:      number;
  pipelineName:    string;
  processedAt:     string;
  overallSuccess:  boolean;
  providerCount:   number;
  successCount:    number;
  aggregatedBody:  Record<string, unknown>;
  providerDetails: ProviderResponseDetail[];
}

// ── ResponseMappingRule — aligne sur MappingType.java ─────────────────────────
// Mêmes constantes que le backend MappingType.java (partagées avec MappingRule)
export type MappingType =
  | 'FIELD_PLACEMENT'
  | 'FORMAT_CHANGE'
  | 'VALUE_TRANSFORM'
  | 'CALCULATED_FIELD'
  | 'RESTRUCTURING';

export interface ResponseMappingRule {
  id:           number;
  pipelineId:   number;
  providerId:   number | null;
  providerName: string | null;
  sourceField:  string;
  targetField:  string;
  mappingType:  MappingType;
  expression:   string | null;
  required:     boolean;
  active:       boolean;
}

export interface CreateResponseMappingRuleRequest {
  sourceField:  string;
  targetField:  string;
  mappingType:  MappingType;
  expression?:  string;
  required:     boolean;
  providerId?:  number | null;
}

export const RESPONSE_MAPPING_TYPES: MappingType[] = [
  'FIELD_PLACEMENT',
  'FORMAT_CHANGE',
  'VALUE_TRANSFORM',
  'CALCULATED_FIELD',
  'RESTRUCTURING',
];

/**
 * Hints d'expression par type — alignés sur ResponseMappingServiceImpl.java :
 *  - FIELD_PLACEMENT  : pas d'expression (copie/renommage simple)
 *  - FORMAT_CHANGE    : UPPERCASE | LOWERCASE | TRIM
 *  - VALUE_TRANSFORM  : CONSTANT:<valeur> ou vide pour conserver la valeur
 *  - CALCULATED_FIELD : expression CONCAT — ex: firstName+' '+lastName
 *  - RESTRUCTURING    : ignoré pour les réponses (valeur copiée telle quelle)
 */
export const EXPRESSION_HINTS: Record<MappingType, string> = {
  FIELD_PLACEMENT:  '',
  FORMAT_CHANGE:    'UPPERCASE · LOWERCASE · TRIM',
  VALUE_TRANSFORM:  'CONSTANT:<fixed_value>  or leave empty to keep source value',
  CALCULATED_FIELD: 'e.g. firstName+\' \'+lastName  (fields separated by +)',
  RESTRUCTURING:    'Value copied as-is (no nested restructuring on responses)',
};

export const NEEDS_EXPRESSION: Record<MappingType, boolean> = {
  FIELD_PLACEMENT:  false,
  FORMAT_CHANGE:    true,
  VALUE_TRANSFORM:  true,
  CALCULATED_FIELD: true,
  RESTRUCTURING:    false,
};

// SOURCE field est ignoré pour CALCULATED_FIELD (expression fournit les sources)
export const SOURCE_HIDDEN: Record<MappingType, boolean> = {
  FIELD_PLACEMENT:  false,
  FORMAT_CHANGE:    false,
  VALUE_TRANSFORM:  false,
  CALCULATED_FIELD: true,
  RESTRUCTURING:    false,
};