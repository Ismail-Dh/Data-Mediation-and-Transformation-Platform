// ── Enums ─────────────────────────────────────────────────────────────────────

export enum DataFormat {
  JSON       = 'JSON',
  XML        = 'XML',
  CSV        = 'CSV',
  PLAIN_TEXT = 'PLAIN_TEXT'
}

export enum PayloadStatus {
  RECEIVED  = 'RECEIVED',   // transitional — rare
  VALIDATED = 'VALIDATED',  // passed structural validation (niveau 1)
  FAILED    = 'FAILED',     // structural violation — stored for audit
  MAPPED    = 'MAPPED',
  SENT      = 'SENT'
}

// ── Payload DTOs ──────────────────────────────────────────────────────────────

export interface PayloadRequest {
  rawContent: string;
  format:     string; // 'JSON' | 'XML' | 'CSV' | 'PLAIN_TEXT'
}

export interface PayloadResponse {
  id:         number;
  rawContent: string;
  format:     DataFormat;
  status:     PayloadStatus;
  receivedAt: string;  // ISO-8601
  pipelineId: number;
}

// ── Violation (niveau 1 structural) ──────────────────────────────────────────

export type ViolationErrorType =
  | 'INVALID_JSON'
  | 'MISSING_FIELD'
  | 'TYPE_MISMATCH'
  | 'NULL_NOT_ALLOWED';

export interface FieldViolation {
  fieldPath:  string;
  errorType:  ViolationErrorType;
  message:    string;
}

/** Shape of the 422 response body from POST /payloads */
export interface PayloadValidationError {
  error:          string;
  timestamp:      string;
  violationCount: number;
  violations:     FieldViolation[];
}

// ── Preview (dry-run) ─────────────────────────────────────────────────────────

export interface ValidationPreviewRequest {
  rawContent: string;
  format:     string;
}

export interface ValidationPreviewResponse {
  valid: boolean;
  structuralOk: boolean;   // niveau 1
  businessOk: boolean;     // niveau 2
  fieldsChecked: number;
  rulesChecked: number;    // nombre de règles métier vérifiées
  violationCount: number;
  violations: FieldViolation[];
}