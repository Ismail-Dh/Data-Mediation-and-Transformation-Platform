// app/services/registry/registry.service.ts

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Registry,
  CreateRegistryRequest,
  UpdateRegistryRequest
} from '../../models/registry.model';
import { environment } from '../../environments/environment';
export interface PushImageRequest {
  registryId: number;
}

export interface PushImageResponse {
  pipelineId:    number;
  registryId:    number;
  registryName:  string;
  imageFullName: string;
  status:        string;
  message:       string;
}
@Injectable({ providedIn: 'root' })
export class RegistryService {
  private readonly base = `${environment.apiUrl}/api/registries`;

  constructor(private http: HttpClient) {}

  getAll(): Observable<Registry[]> {
    return this.http.get<Registry[]>(this.base);
  }

  getById(id: number): Observable<Registry> {
    return this.http.get<Registry>(`${this.base}/${id}`);
  }

  create(req: CreateRegistryRequest): Observable<Registry> {
    return this.http.post<Registry>(this.base, req);
  }

  update(id: number, req: UpdateRegistryRequest): Observable<Registry> {
    return this.http.patch<Registry>(`${this.base}/${id}`, req);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
  pushImage(pipelineId: number, registryId: number): Observable<PushImageResponse> {
  return this.http.post<PushImageResponse>(
    `${environment.apiUrl}/api/pipelines/${pipelineId}/image/push`,
    { registryId }
  );
}
}