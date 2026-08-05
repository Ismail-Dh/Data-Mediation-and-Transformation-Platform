import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Pipeline,
  CreatePipelineRequest,
  UpdatePipelineRequest,
  PipelineDetails
} from '../../models/pipeline';

import { environment } from '../../environments/environment';


@Injectable({ providedIn: 'root' })
export class PipelineService {
  private readonly base = `${environment.apiUrl}/api/pipelines`;

  constructor(private http: HttpClient) {}

  /** ADMIN — tous les pipelines */
  getAll(): Observable<Pipeline[]> {
    return this.http.get<Pipeline[]>(this.base);
  }

  /** DEVELOPER — seulement les siens */
  getMine(): Observable<Pipeline[]> {
    return this.http.get<Pipeline[]>(`${this.base}/my`);
  }

  getById(id: number): Observable<Pipeline> {
    return this.http.get<Pipeline>(`${this.base}/${id}`);
  }

  /**
   * Vue consolidée d'un pipeline : infos générales + schema fields +
   * validation rules + mapping rules + response mapping rules, en un seul
   * appel HTTP. Utilisée par l'écran Admin (et le détail Developer).
   */
  getFullDetails(id: number): Observable<PipelineDetails> {
    return this.http.get<PipelineDetails>(`${this.base}/${id}/full-details`);
  }

  create(req: CreatePipelineRequest): Observable<Pipeline> {
    return this.http.post<Pipeline>(this.base, req);
  }

  update(id: number, req: UpdatePipelineRequest): Observable<Pipeline> {
    return this.http.patch<Pipeline>(`${this.base}/${id}`, req);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
  validatePipeline(pipelineId: number): Observable<Pipeline> {
  return this.http.patch<Pipeline>(
    `${environment.apiUrl}/api/pipelines/${pipelineId}/validate`, {}
  );
}

revertPipeline(pipelineId: number): Observable<Pipeline> {
  return this.http.patch<Pipeline>(
    `${environment.apiUrl}/api/pipelines/${pipelineId}/revert`, {}
  );
}
  
}