import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuditLogService } from '../../services/audit-log/audit-log.service';
import { AuditLog, AuditLogFilter } from '../../models/audit-log';

@Component({
  selector: 'app-audit-log-admin',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './audit-log-admin.component.html',
  styleUrl: './audit-log-admin.component.scss'
})
export class AuditLogAdminComponent implements OnInit {

  logs: AuditLog[] = [];
  loading = false;
  
  selectedLog: AuditLog | null = null;

  filter: AuditLogFilter = {
    username:   '',
    role:       '',
    action:     '',
    httpStatus: undefined
  };

  readonly ACTIONS   = ['CREATE', 'UPDATE', 'DELETE', 'READ'];
  readonly ROLES     = ['ROLE_ADMIN', 'ROLE_USER', 'ROLE_DEVELOPER'];
  readonly STATUSES  = [200, 201, 400, 401, 403, 404, 500];

  constructor(
    private auditLogService: AuditLogService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.load();
  }

  openDetail(log: AuditLog): void {
    this.selectedLog = log;
  }

  closeDetail(): void {
    this.selectedLog = null;
  }

  load(): void {
    this.loading = true;

    const cleanFilter: AuditLogFilter = {};
    if (this.filter.username)   cleanFilter.username   = this.filter.username;
    if (this.filter.role)       cleanFilter.role       = this.filter.role;
    if (this.filter.action)     cleanFilter.action     = this.filter.action;
    if (this.filter.httpStatus) cleanFilter.httpStatus = this.filter.httpStatus;

    this.auditLogService.getAllLogs(cleanFilter).subscribe({
      next: data => {
        this.logs    = data;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  resetFilters(): void {
    this.filter = { username: '', role: '', action: '', httpStatus: undefined };
    this.load();
  }

  getStatusClass(status: number | null | undefined): string {
    if (!status) return '';
    if (status < 300) return 'badge-success';
    if (status < 400) return 'badge-warning';
    return 'badge-error';
  }
}