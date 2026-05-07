import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TemplateResponse, TemplateRequest, TemplateType } from '../../models/template';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class TemplateService {
  private readonly adminBase = `${environment.apiUrl}/api/admin/templates`;
  private readonly devBase   = `${environment.apiUrl}/api/templates`;

  constructor(private http: HttpClient) {}

  // ── Admin ────────────────────────────────────────────────────────────────
  create(req: TemplateRequest): Observable<TemplateResponse> {
    return this.http.post<TemplateResponse>(this.adminBase, req);
  }

  findAll(type: TemplateType): Observable<TemplateResponse[]> {
    return this.http.get<TemplateResponse[]>(this.adminBase, { params: new HttpParams().set('type', type) });
  }

  findById(id: number, type: TemplateType): Observable<TemplateResponse> {
    return this.http.get<TemplateResponse>(`${this.adminBase}/${id}`, { params: new HttpParams().set('type', type) });
  }

  update(id: number, type: TemplateType, req: TemplateRequest): Observable<TemplateResponse> {
    return this.http.put<TemplateResponse>(`${this.adminBase}/${id}`, req, { params: new HttpParams().set('type', type) });
  }

  publish(id: number, type: TemplateType): Observable<TemplateResponse> {
    return this.http.put<TemplateResponse>(`${this.adminBase}/${id}/publish`, {}, { params: new HttpParams().set('type', type) });
  }

  disable(id: number, type: TemplateType): Observable<TemplateResponse> {
    return this.http.put<TemplateResponse>(`${this.adminBase}/${id}/disable`, {}, { params: new HttpParams().set('type', type) });
  }

  // ── Developer ────────────────────────────────────────────────────────────
  findPublished(type: TemplateType): Observable<TemplateResponse[]> {
    return this.http.get<TemplateResponse[]>(this.devBase, { params: new HttpParams().set('type', type) });
  }
}