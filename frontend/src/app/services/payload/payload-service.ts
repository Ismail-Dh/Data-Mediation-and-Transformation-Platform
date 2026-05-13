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
   * Soumet un payload. Lance une erreur HTTP 422 si la validation structurelle échoue.
   * L'erreur contient un corps { error, timestamp, violationCount, violations[] }.
   */
  receivePayload(pipelineId: number, request: PayloadRequest): Observable<PayloadResponse> {
    return this.http.post<PayloadResponse>(
      `${this.base}/api/pipelines/${pipelineId}/payloads`,
      request
    );
  }

  /**
   * GET /api/pipelines/{pipelineId}/payloads
   */
  getPayloadsByPipeline(pipelineId: number): Observable<PayloadResponse[]> {
    return this.http.get<PayloadResponse[]>(
      `${this.base}/api/pipelines/${pipelineId}/payloads`
    );
  }

  /**
   * GET /api/pipelines/{pipelineId}/payloads/{payloadId}
   */
  getPayloadById(pipelineId: number, payloadId: number): Observable<PayloadResponse> {
    return this.http.get<PayloadResponse>(
      `${this.base}/api/pipelines/${pipelineId}/payloads/${payloadId}`
    );
  }

  // ── Dry-run preview (niveau 1 structural) ─────────────────────────────────

  /**
   * POST /api/pipelines/{pipelineId}/validation/preview
   * Teste la validation structurelle SANS persister le payload.
   * Retourne toujours 200 avec { valid, violations[] }.
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