export type RuleType =
  | 'NOT_NULL'
  | 'TYPE_NUMBER'
  | 'TYPE_DATE'
  | 'REGEX_EMAIL'
  |  'REGEX_PASSWORD'
  | 'REGEX_PHONE'
  | 'MIN_MAX_LENGTH'
  |  'REGEX';

export interface ValidationRule {
  id: number;
  fieldName: string;
  ruleType: RuleType;
  pattern: string | null;
  description: string | null;
  active: boolean;
  global: boolean;
}

export interface CreateValidationRuleRequest {
  fieldName: string;
  ruleType: RuleType;
  pattern: string | null;
  description: string | null;
  active: boolean;
}

export interface UpdateValidationRuleRequest {
  fieldName: string;
  ruleType: RuleType;
  pattern: string | null;
  description: string | null;
  active: boolean;
}
export interface PipelineValidationRuleRequest {
  globalRuleId?: number;       // attacher une règle globale existante
  fieldName?: string;          // créer une règle privée
  ruleType?: RuleType;
  pattern?: string | null;
  active?: boolean;
}

export interface PipelineValidationRuleResponse {
  id: number;
  fieldName: string;
  ruleType: RuleType;
  pattern: string | null;
  description: string | null;
  active: boolean;
  global: boolean;             // true = vient du catalogue Admin, false = règle privée
}