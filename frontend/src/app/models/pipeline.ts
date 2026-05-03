export type PipelineStatus = 'DRAFT' | 'CONFIGURED' | 'VALIDATED';
export type DataFormat = 'JSON' | 'XML' | 'CSV' | 'PLAIN_TEXT';

export interface Pipeline {
  id: number;
  name: string;
  version: string;
  createdAt: string;
  inputFormat: DataFormat;
  outputFormat: DataFormat;
  status: PipelineStatus;
  createdBy: string;
  providerId: number | null;
  providerName: string | null;
  providerEndpoint: string | null;
}

export interface CreatePipelineRequest {
  name: string;
  version?: string;
  inputFormat: string;
  outputFormat: string;
  providerId?: number | null;
}

export interface UpdatePipelineRequest {
  name?: string;
  version?: string;
  inputFormat?: string;
  outputFormat?: string;
  providerId?: number | null;
}