import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuditLogService } from '../../services/audit-log/audit-log.service';
import { AuditLog } from '../../models/audit-log';

@Component({
  selector: 'app-audit-log-me',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './audit-log-me.component.html',
  styleUrl: './audit-log-me.component.scss'
})
export class AuditLogMeComponent implements OnInit {

  logs: AuditLog[]             = [];
  loading                      = false;
  successCount                 = 0;
  errorCount                   = 0;
  selectedLog: AuditLog | null = null;

  constructor(
    private auditLogService: AuditLogService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.auditLogService.getMyLogs().subscribe({
      next: data => {
        this.logs         = data;
        this.successCount = data.filter(l => l.httpStatus && l.httpStatus < 400).length;
        this.errorCount   = data.filter(l => l.httpStatus && l.httpStatus >= 400).length;
        this.loading      = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  openDetail(log: AuditLog): void {
    this.selectedLog = log;
    this.cdr.detectChanges();
  }

  closeDetail(): void {
    this.selectedLog = null;
    this.cdr.detectChanges();
  }

  getStatusClass(status: number | null): string {
    if (!status) return '';
    if (status < 300) return 'badge-success';
    if (status < 400) return 'badge-warning';
    return 'badge-error';
  }
}