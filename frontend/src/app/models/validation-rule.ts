export type RuleType =
  | 'NOT_NULL'
  | 'TYPE_NUMBER'
  | 'TYPE_DATE'
  | 'REGEX_EMAIL'
  |  'REGEX_PASSWORD'
  | 'REGEX_PHONE'
  | 'MIN_MAX_LENGTH';

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