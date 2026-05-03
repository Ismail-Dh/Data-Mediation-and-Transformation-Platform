export interface Provider {
  id: number;
  name: string;
  endpoint: string;
  protocol: string;
  timeout: number;
}

export interface CreateProviderRequest {
  name: string;
  endpoint: string;
  protocol: string;
  timeout: number;
}

export interface UpdateProviderRequest {
  name?: string;
  endpoint?: string;
  protocol?: string;
  timeout?: number;
}