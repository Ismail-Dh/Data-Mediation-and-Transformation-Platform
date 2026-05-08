import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuditLog, AuditLogFilter } from '../../models/audit-log';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AuditLogService {
  private readonly base = `${environment.apiUrl}/api/audit-logs`;

  constructor(private http: HttpClient) {}

  // ADMIN — tous les logs avec filtres
  getAllLogs(filter: AuditLogFilter = {}): Observable<AuditLog[]> {
    let params = new HttpParams();
    if (filter.username)   params = params.set('username',   filter.username);
    if (filter.role)       params = params.set('role',       filter.role);
    if (filter.action)     params = params.set('action',     filter.action);
    if (filter.httpStatus) params = params.set('httpStatus', filter.httpStatus);
    return this.http.get<AuditLog[]>(`${this.base}/admin`, { params });
  }

  // DEVELOPER — ses propres logs
  getMyLogs(): Observable<AuditLog[]> {
    return this.http.get<AuditLog[]>(`${this.base}/me`);
  }
}