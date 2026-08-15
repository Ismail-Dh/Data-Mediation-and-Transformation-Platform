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

/** Une entrée du fichier JSON de schéma qui n'a pas pu être importée. */
export interface PipelineFieldImportError {
  fieldPath: string | null;
  reason:    string;
}

/** Réponse de POST /api/pipelines/{pipelineId}/fields/import */
export interface PipelineFieldImportResponse {
  totalRequested: number;
  created:        number;
  skipped:        number;
  fields:         PipelineFieldResponse[];
  errors:         PipelineFieldImportError[];
}