import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { PipelineFieldRequest ,  PipelineFieldResponse }  from '../../models/pipelineField';

@Injectable({ providedIn: 'root' })
export class PipelineFieldService {

  private base = environment.apiUrl; 

  constructor(private http: HttpClient) {}

  /**
   * POST /api/pipelines/{pipelineId}/fields
   * Ajoute un champ au schéma du pipeline.
   */
  addField(pipelineId: number, request: PipelineFieldRequest): Observable<PipelineFieldResponse> {
    return this.http.post<PipelineFieldResponse>(
      `${this.base}/api/pipelines/${pipelineId}/fields`,
      request
    );
  }

  /**
   * GET /api/pipelines/{pipelineId}/fields
   * Récupère tous les champs du schéma.
   */
  getFields(pipelineId: number): Observable<PipelineFieldResponse[]> {
    return this.http.get<PipelineFieldResponse[]>(
      `${this.base}/api/pipelines/${pipelineId}/fields`
    );
  }

  /**
   * PUT /api/pipelines/{pipelineId}/fields/{fieldId}
   * Met à jour un champ existant.
   */
  updateField(
    pipelineId: number,
    fieldId: number,
    request: PipelineFieldRequest
  ): Observable<PipelineFieldResponse> {
    return this.http.put<PipelineFieldResponse>(
      `${this.base}/api/pipelines/${pipelineId}/fields/${fieldId}`,
      request
    );
  }

  /**
   * DELETE /api/pipelines/{pipelineId}/fields/{fieldId}
   * Supprime un champ. Retourne 204 No Content.
   */
  deleteField(pipelineId: number, fieldId: number): Observable<void> {
    return this.http.delete<void>(
      `${this.base}/api/pipelines/${pipelineId}/fields/${fieldId}`
    );
  }
}