import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ValidationRule, CreateValidationRuleRequest, UpdateValidationRuleRequest } from '../../models/validation-rule';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class ValidationRuleService {
  private readonly apiUrl = `${environment.apiUrl}/api/admin/rules`;

  constructor(private http: HttpClient) {}

  getRules(): Observable<ValidationRule[]> {
    return this.http.get<ValidationRule[]>(this.apiUrl);
  }

  getRuleById(id: number): Observable<ValidationRule> {
    return this.http.get<ValidationRule>(`${this.apiUrl}/${id}`);
  }

  createRule(data: CreateValidationRuleRequest): Observable<ValidationRule> {
    return this.http.post<ValidationRule>(this.apiUrl, data);
  }

  updateRule(id: number, data: UpdateValidationRuleRequest): Observable<ValidationRule> {
    return this.http.put<ValidationRule>(`${this.apiUrl}/${id}`, data);
  }

  deactivateRule(id: number): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/${id}/deactivate`, {});
  }

  deleteRule(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}