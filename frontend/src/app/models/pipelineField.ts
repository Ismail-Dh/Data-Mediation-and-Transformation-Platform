export enum FieldType {
  STRING  = 'STRING',
  INTEGER = 'INTEGER',
  BOOLEAN = 'BOOLEAN',
  OBJECT  = 'OBJECT',
  ARRAY   = 'ARRAY'
}
export interface PipelineFieldRequest {
  fieldPath: string;
  fieldType: FieldType;
  required:  boolean;
  nullable:  boolean;
}


export interface PipelineFieldResponse {
  id:        number;
  fieldPath: string;
  fieldType: FieldType;
  required:  boolean;
  nullable:  boolean;
}