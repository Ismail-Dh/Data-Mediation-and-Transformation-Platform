export interface SandboxRequest {
  rawContent: string;
  format:     string;
}

export interface FieldViolation {
  fieldPath:  string;
  errorType:  string;
  message:    string;
}

export interface MappingRuleSummary {
  mappingType:  string;
  sourceField:  string;
  targetField:  string;
  expression:   string | null;
  applied:      boolean;
  valueBefore:  string | null;
  valueAfter:   string | null;
}

export interface SandboxResponse {
  pipelineId:        number;
  payloadId:         number;
  validationPassed:  boolean;
  validationMessage: string;
  mappingApplied:    boolean;
  originalPayload:   Record<string, unknown> | null;
  mappedPayload:     Record<string, unknown> | null;
  payloadStatus:     string;
  pipelineStatus:    string;
  violations:        FieldViolation[];
  mappingSummary:    MappingRuleSummary[];
}

export interface SandboxLogResponse {
  id:                number;
  pipelineId:        number;
  payloadId:         number | null;
  validationPassed:  boolean;
  mappingApplied:    boolean;
  validationMessage: string;
  durationMs:        number | null;
  executedAt:        string;
  inputFormat:       string;
  rawContent:        string | null;
  failureStep:       'STRUCTURAL' | 'RULES' | null;
  violations:        string | null;
  originalPayload:   string | null;
  mappedPayload:     string | null;
  mappingSummary:    string | null;
}

export interface SandboxLogsPage {
  content:       SandboxLogResponse[];
  totalElements: number;
  totalPages:    number;
  number:        number;
  size:          number;
}

export interface ParsedSandboxLog {
  log:             SandboxLogResponse;
  violations:      FieldViolation[];
  originalPayload: Record<string, unknown> | null;
  mappedPayload:   Record<string, unknown> | null;
  mappingSummary:  MappingRuleSummary[];
}