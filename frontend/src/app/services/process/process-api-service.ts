import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  ProcessRequest,
  ProcessResponse,
  ResponseMappingRule,
  CreateResponseMappingRuleRequest,
} from '../../models/process.model';

@Injectable({ providedIn: 'root' })
export class ProcessApiService {

  private readonly base = `${environment.apiUrl}`;

  constructor(private http: HttpClient) {}

  // ── POST /api/process ──────────────────────────────────────────────────────
  runProcess(req: ProcessRequest): Observable<ProcessResponse> {
    return this.http.post<ProcessResponse>(`${this.base}/api/process`, req);
  }

  // ── /api/pipelines/{id}/response-rules ────────────────────────────────────
  getRules(pipelineId: number): Observable<ResponseMappingRule[]> {
    return this.http.get<ResponseMappingRule[]>(
      `${this.base}/api/pipelines/${pipelineId}/response-rules`
    );
  }

  createRule(pipelineId: number, req: CreateResponseMappingRuleRequest): Observable<ResponseMappingRule> {
    return this.http.post<ResponseMappingRule>(
      `${this.base}/api/pipelines/${pipelineId}/response-rules`, req
    );
  }

  updateRule(pipelineId: number, ruleId: number, req: CreateResponseMappingRuleRequest): Observable<ResponseMappingRule> {
    return this.http.put<ResponseMappingRule>(
      `${this.base}/api/pipelines/${pipelineId}/response-rules/${ruleId}`, req
    );
  }

  deleteRule(pipelineId: number, ruleId: number): Observable<void> {
    return this.http.delete<void>(
      `${this.base}/api/pipelines/${pipelineId}/response-rules/${ruleId}`
    );
  }
}