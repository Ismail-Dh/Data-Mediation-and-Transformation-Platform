import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface DockerImageBuildResponse {
  imageId: number;
  imageName: string;
  tag: string;
  status: string;
  message: string;
}

export interface DockerImageResponse {
  id: number;
  imageName: string;
  tag: string;
  status: 'PENDING' | 'BUILDING' | 'SUCCESS' | 'FAILED';
  sizeBytes: number | null;
  builtAt: string | null;
  pipelineId: number;
}
export interface BuildLogEntryResponse {
  id:              number;
  tag:             string;
  version:         string | null;
  status:          'SUCCESS' | 'FAILED' | 'BUILDING' | 'PENDING';
  startTime:       string | null;
  endTime:         string | null;
  durationSeconds: number | null;
}



/** Événement final SSE BUILD_COMPLETE */
export interface BuildCompleteEvent {
  type:     'BUILD_COMPLETE';
  imageId:  number;
  size:     number;
  duration: number;
}
 
/** Événement final SSE BUILD_FAILED */
export interface BuildFailedEvent {
  type:         'BUILD_FAILED';
  errorMessage: string;
}

@Injectable({ providedIn: 'root' })
export class DockerImageService {

  private baseUrl = 'http://localhost:8080/api/pipelines';

  constructor(private http: HttpClient) {}

  generateImage(pipelineId: number): Observable<DockerImageBuildResponse> {
    return this.http.post<DockerImageBuildResponse>(
      `${this.baseUrl}/${pipelineId}/image/generate`,
      {}
    );
  }

  getImageInfo(pipelineId: number): Observable<DockerImageResponse> {
    return this.http.get<DockerImageResponse>(
      `${this.baseUrl}/${pipelineId}/image`
    );
  }
  getVersionHistory(pipelineId: number): Observable<BuildLogEntryResponse[]> {
  return this.http.get<BuildLogEntryResponse[]>(
    `${this.baseUrl}/${pipelineId}/image/versions`
  );
  }

  downloadImage(pipelineId: number): void {
    // Trigger browser download via anchor tag
    const token = localStorage.getItem('token');
    const url = `${this.baseUrl}/${pipelineId}/image/download`;

    this.http.get(url, { responseType: 'blob' }).subscribe(blob => {
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = `pipeline-${pipelineId}.tar`;
      a.click();
      URL.revokeObjectURL(a.href);
    });
  }

  // ── SSE streaming (tâche 5.4) ─────────────────────────────────────────────────
 
  /**
   * Ouvre un EventSource SSE vers /build-logs/stream.
   *
   * Le JWT est passé en query param (?token=...) car l'API EventSource
   * native du navigateur ne permet PAS d'envoyer des headers personnalisés.
   * Le filtre JwtAuthenticationFilter accepte ce mode uniquement sur
   * les routes terminant par /build-logs/stream.
   */
  streamBuildLogs(
    pipelineId: number,
    handlers: {
      onLog:      (line: string)              => void;
      onComplete: (event: BuildCompleteEvent) => void;
      onFailed:   (event: BuildFailedEvent)   => void;
      onError?:   (err: Event)               => void;
    }
  ): EventSource {
    const token = this.getStoredToken();
    const url   = `${this.baseUrl}/${pipelineId}/build-logs/stream`
                + (token ? `?token=${encodeURIComponent(token)}` : '');
 
    const es = new EventSource(url);
 
    // Lignes brutes de docker build → onmessage
    es.onmessage = (e: MessageEvent) => handlers.onLog(e.data);
 
    // Événement BUILD_COMPLETE
    es.addEventListener('BUILD_COMPLETE', (e: MessageEvent) => {
      try { handlers.onComplete(JSON.parse(e.data)); } catch { /* ignore */ }
      es.close();
    });
 
    // Événement BUILD_FAILED
    es.addEventListener('BUILD_FAILED', (e: MessageEvent) => {
      try { handlers.onFailed(JSON.parse(e.data)); } catch { /* ignore */ }
      es.close();
    });
 
    // Erreur réseau ou connexion perdue
    es.onerror = (err: Event) => {
      handlers.onError?.(err);
      es.close();
    };
 
    return es;
  }
 
  // ── Helper token ──────────────────────────────────────────────────────────────
 
  /**
   * Récupère le JWT depuis le localStorage.
   * Adapte la clé ('token', 'jwt', 'access_token'…) à celle utilisée
   * par ton service d'authentification Angular.
   */
  private getStoredToken(): string | null {
    return localStorage.getItem('token')
        ?? localStorage.getItem('jwt')
        ?? localStorage.getItem('access_token')
        ?? null;
  }
}

