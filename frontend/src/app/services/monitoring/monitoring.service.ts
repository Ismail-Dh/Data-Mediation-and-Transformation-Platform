import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { MonitoringStatsResponse } from '../../models/monitoring.model';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class MonitoringService {
  private readonly apiUrl = `${environment.apiUrl}/api/admin/monitoring`;

  constructor(private http: HttpClient) {}

  getStats(): Observable<MonitoringStatsResponse> {
    return this.http.get<MonitoringStatsResponse>(`${this.apiUrl}/stats`);
  }
}