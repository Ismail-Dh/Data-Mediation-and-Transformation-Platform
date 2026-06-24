// app/models/registry.model.ts

export interface Registry {
  id: number;
  name: string;
  url: string;
  username: string;
  // password is never returned by the API
}

export interface CreateRegistryRequest {
  name: string;
  url: string;
  username: string;
  password: string;
}

export interface UpdateRegistryRequest {
  name?: string;
  url?: string;
  username?: string;
  password?: string;
}