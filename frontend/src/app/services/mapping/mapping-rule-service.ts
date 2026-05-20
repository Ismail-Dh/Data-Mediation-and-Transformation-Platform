import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { MappingRuleRequest, MappingRuleResponse } from '../../models/mapping-rule';
import { MappingResultResponse } from '../../models/mapping-result';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class MappingRuleService {
 
  private base(pipelineId: number): string {
    return `${this.apiUrl}/api/pipelines/${pipelineId}/mappings`;
  }
  private readonly apiUrl = `${environment.apiUrl}`;
  constructor(private http: HttpClient) {}

  /** POST /api/pipelines/{id}/mappings — create a rule */
  createRule(pipelineId: number, body: MappingRuleRequest): Observable<MappingRuleResponse> {
    return this.http.post<MappingRuleResponse>(this.base(pipelineId), body);
  }

  /** GET /api/pipelines/{id}/mappings — active rules only */
  getRules(pipelineId: number): Observable<MappingRuleResponse[]> {
    return this.http.get<MappingRuleResponse[]>(this.base(pipelineId));
  }

  /** GET /api/pipelines/{id}/mappings/all — active + inactive */
  getAllRules(pipelineId: number): Observable<MappingRuleResponse[]> {
    return this.http.get<MappingRuleResponse[]>(`${this.base(pipelineId)}/all`);
  }

  /** DELETE /api/pipelines/{id}/mappings/{ruleId} — soft delete (active = false) */
  deleteRule(pipelineId: number, ruleId: number): Observable<void> {
    return this.http.delete<void>(`${this.base(pipelineId)}/${ruleId}`);
  }

  /** PATCH /api/pipelines/{id}/mappings/{ruleId}/activate — re-activate a rule */
  activateRule(pipelineId: number, ruleId: number): Observable<MappingRuleResponse> {
    return this.http.patch<MappingRuleResponse>(`${this.base(pipelineId)}/${ruleId}/activate`, {});
  }

  /** POST /api/pipelines/{id}/mappings/apply — test the mapping on a raw payload */
  applyMapping(pipelineId: number, rawContent: string): Observable<MappingResultResponse> {
    return this.http.post<MappingResultResponse>(
      `${this.base(pipelineId)}/apply`,
      { rawContent }
    );
  }
}