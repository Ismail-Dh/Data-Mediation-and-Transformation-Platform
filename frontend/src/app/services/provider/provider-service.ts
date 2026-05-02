import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Provider,
  CreateProviderRequest,
  UpdateProviderRequest
} from '../../models/provider';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class ProviderService {
  private readonly base = `${environment.apiUrl}/api/providers`;

  constructor(private http: HttpClient) {}

  getAll(): Observable<Provider[]> {
    return this.http.get<Provider[]>(this.base);
  }

  getById(id: number): Observable<Provider> {
    return this.http.get<Provider>(`${this.base}/${id}`);
  }

  create(req: CreateProviderRequest): Observable<Provider> {
    return this.http.post<Provider>(this.base, req);
  }

  update(id: number, req: UpdateProviderRequest): Observable<Provider> {
    return this.http.patch<Provider>(`${this.base}/${id}`, req);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
