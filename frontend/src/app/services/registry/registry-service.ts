// app/services/registry/registry.service.ts

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Registry,
  CreateRegistryRequest,
  UpdateRegistryRequest
} from '../../models/registry.model';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class RegistryService {
  private readonly base = `${environment.apiUrl}/api/registries`;

  constructor(private http: HttpClient) {}

  getAll(): Observable<Registry[]> {
    return this.http.get<Registry[]>(this.base);
  }

  getById(id: number): Observable<Registry> {
    return this.http.get<Registry>(`${this.base}/${id}`);
  }

  create(req: CreateRegistryRequest): Observable<Registry> {
    return this.http.post<Registry>(this.base, req);
  }

  update(id: number, req: UpdateRegistryRequest): Observable<Registry> {
    return this.http.patch<Registry>(`${this.base}/${id}`, req);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}