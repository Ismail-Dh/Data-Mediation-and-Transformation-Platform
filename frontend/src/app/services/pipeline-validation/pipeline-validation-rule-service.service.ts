import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  ValidationRule,
  PipelineValidationRuleRequest,
  PipelineValidationRuleResponse
} from '../../models/validation-rule';

@Injectable({ providedIn: 'root' })
export class PipelineValidationRuleService {

  private base = environment.apiUrl;

  constructor(private http: HttpClient) {}

  // règles globales actives (catalogue Admin)
  getGlobalRules(): Observable<ValidationRule[]> {
    return this.http.get<ValidationRule[]>(
      `${this.base}/api/admin/rules/active`
    );
  }

  // règles d'une pipeline
  getRules(pipelineId: number): Observable<PipelineValidationRuleResponse[]> {
    return this.http.get<PipelineValidationRuleResponse[]>(
      `${this.base}/api/pipelines/${pipelineId}/validation-rules`
    );
  }

  // attacher globale OU créer privée
  addRule(pipelineId: number, request: PipelineValidationRuleRequest): Observable<PipelineValidationRuleResponse> {
    return this.http.post<PipelineValidationRuleResponse>(
      `${this.base}/api/pipelines/${pipelineId}/validation-rules`,
      request
    );
  }

  // modifier une règle privée
  updateRule(pipelineId: number, ruleId: number, request: PipelineValidationRuleRequest): Observable<PipelineValidationRuleResponse> {
    return this.http.put<PipelineValidationRuleResponse>(
      `${this.base}/api/pipelines/${pipelineId}/validation-rules/${ruleId}`,
      request
    );
  }

  // toggle active/inactive
  toggleRule(pipelineId: number, ruleId: number): Observable<PipelineValidationRuleResponse> {
    return this.http.patch<PipelineValidationRuleResponse>(
      `${this.base}/api/pipelines/${pipelineId}/validation-rules/${ruleId}/toggle`,
      {}
    );
  }

  // supprimer
  deleteRule(pipelineId: number, ruleId: number): Observable<void> {
    return this.http.delete<void>(
      `${this.base}/api/pipelines/${pipelineId}/validation-rules/${ruleId}`
    );
  }
}