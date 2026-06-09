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
}
