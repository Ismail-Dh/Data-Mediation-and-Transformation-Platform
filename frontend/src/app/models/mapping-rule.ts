// Mirrors MappingRuleResponse.java
export interface MappingRuleResponse {
  id:          number;
  sourceField: string;
  targetField: string;
  mappingType: MappingType;
  expression:  string;
  active:      boolean;
  pipelineId:  number;
}

// Mirrors MappingRuleRequest.java
export interface MappingRuleRequest {
  sourceField: string;
  targetField: string;
  mappingType: MappingType;
  expression:  string;
}

// Mirrors MappingType.java enum
export type MappingType =
  | 'FIELD_PLACEMENT'
  | 'FORMAT_CHANGE'
  | 'VALUE_TRANSFORM'
  | 'CALCULATED_FIELD'
  | 'RESTRUCTURING';