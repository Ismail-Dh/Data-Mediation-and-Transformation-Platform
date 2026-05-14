import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  PayloadRequest,
  PayloadResponse,
  ValidationPreviewRequest,
  ValidationPreviewResponse
} from '../../models/payload';

@Injectable({ providedIn: 'root' })
export class PayloadService {

  private base = environment.apiUrl;

  constructor(private http: HttpClient) {}

  // ── Payload CRUD ───────────────────────────────────────────────────────────

  /**
   * POST /api/pipelines/{pipelineId}/payloads
   * Soumet un payload. Retourne 422 si niveau-1 ou niveau-2 échoue.
   * Corps 422 : { error, timestamp, violationCount, violations[] }
   */
  receivePayload(pipelineId: number, request: PayloadRequest): Observable<PayloadResponse> {
    return this.http.post<PayloadResponse>(
      `${this.base}/api/pipelines/${pipelineId}/payloads`,
      request
    );
  }

  getPayloadsByPipeline(pipelineId: number): Observable<PayloadResponse[]> {
    return this.http.get<PayloadResponse[]>(
      `${this.base}/api/pipelines/${pipelineId}/payloads`
    );
  }

  getPayloadById(pipelineId: number, payloadId: number): Observable<PayloadResponse> {
    return this.http.get<PayloadResponse>(
      `${this.base}/api/pipelines/${pipelineId}/payloads/${payloadId}`
    );
  }

  // ── Dry-run preview (niveau 1 + niveau 2) ─────────────────────────────────

  /**
   * POST /api/pipelines/{pipelineId}/validation/preview
   * Teste niveau-1 (structure) puis niveau-2 (règles métier) SANS persister.
   * Retourne toujours 200 avec { valid, structuralOk, businessOk, violations[] }.
   */
  previewValidation(
    pipelineId: number,
    request: ValidationPreviewRequest
  ): Observable<ValidationPreviewResponse> {
    return this.http.post<ValidationPreviewResponse>(
      `${this.base}/api/pipelines/${pipelineId}/validation/preview`,
      request
    );
  }
}