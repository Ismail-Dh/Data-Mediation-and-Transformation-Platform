// Mirrors MappingResultResponse.java
export interface MappingResultResponse {
  pipelineId: number;
  original:   Record<string, unknown>;
  mapped:     Record<string, unknown>;
}