export type TemplateType   = 'VALIDATION' | 'MAPPING';
export type TemplateStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED';

export interface TemplateResponse {
  id:          number;
  name:        string;
  description: string | null;
  type:        TemplateType;
  content:     Record<string, any>;
  status:      TemplateStatus;
  version:     number;
  parentId:    number | null;
  createdBy:   string;
  createdAt:   string;
  updatedAt:   string;
}

export interface TemplateRequest {
  name:        string;
  description: string | null;
  type:        TemplateType;
  content:     Record<string, any>;
}