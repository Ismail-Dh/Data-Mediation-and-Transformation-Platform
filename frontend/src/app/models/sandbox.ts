// src/app/models/sandbox.ts
export interface SandboxRequest {
  rawContent: string;
  format:     string;
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
}