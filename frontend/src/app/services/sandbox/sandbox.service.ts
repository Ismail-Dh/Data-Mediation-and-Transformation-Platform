// src/app/services/sandbox/sandbox-service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SandboxRequest, SandboxResponse } from '../../models/sandbox';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class SandboxService {

  private base(pipelineId: number): string {
    return `${environment.apiUrl}/api/pipelines/${pipelineId}/sandbox`;
  }

  constructor(private http: HttpClient) {}

  run(pipelineId: number, request: SandboxRequest): Observable<SandboxResponse> {
    return this.http.post<SandboxResponse>(`${this.base(pipelineId)}/run`, request);
  }
}