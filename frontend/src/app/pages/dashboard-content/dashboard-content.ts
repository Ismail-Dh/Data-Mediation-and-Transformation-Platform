import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule }      from '@angular/common';
import { forkJoin, of }      from 'rxjs';
import { catchError, finalize } from 'rxjs/operators';

import { AuthService }       from '../../services/auth/auth-service';
import { MonitoringService } from '../../services/monitoring/monitoring.service';
import { AuditLogService }   from '../../services/audit-log/audit-log.service';
import { PipelineService }   from '../../services/pipeline/pipeline-service';
import { PayloadService }    from '../../services/payload/payload-service';
import { SandboxService }    from '../../services/sandbox/sandbox.service';

import { MonitoringStatsResponse } from '../../models/monitoring.model';
import { AuditLog }          from '../../models/audit-log';
import { Pipeline }          from '../../models/pipeline';
import { PayloadResponse }   from '../../models/payload';
import { SandboxLogResponse, SandboxLogsPage } from '../../models/sandbox';

export interface KpiCard {
  label:     string;
  value:     string;
  sub:       string;
  trend?:    'up' | 'down';
  trendVal?: string;
  color?:    'default' | 'success' | 'danger' | 'info';
  icon:      string;
}
export interface ActionBar   { action: string; total: number; errors: number; pct: number; }
export interface HourBar     { hour: string; requests: number; errors: number; }
export interface StatusCount { status: string; count: number; pct: number; }
export interface ActivityItem { label: string; sub: string; timeAgo: string; outcome: 'ok' | 'warn' | 'error'; }

@Component({
  selector:    'app-dashboard-content',
  standalone:  true,
  imports:     [CommonModule],
  templateUrl: './dashboard-content.html',
  styleUrl:    './dashboard-content.scss',
})
export class DashboardContent implements OnInit {

  isAdmin  = false;
  username = '';
  loading  = true;
  error    = false;
  today    = new Date().toLocaleDateString('en-US', {
    weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
  });

  kpis:             KpiCard[]      = [];
  activityFeed:     ActivityItem[] = [];
  payloadBreakdown: StatusCount[]  = [];

  hourly:            HourBar[]     = [];
  maxRequests        = 1;
  actions:           ActionBar[]   = [];
  pipelineBreakdown: StatusCount[] = [];

  myPipelines: Pipeline[]           = [];
  sandboxRuns: SandboxLogResponse[] = [];

  constructor(
    private auth:       AuthService,
    private monitoring: MonitoringService,
    private auditSvc:   AuditLogService,
    private pipeSvc:    PipelineService,
    private payloadSvc: PayloadService,
    private sandboxSvc: SandboxService,
    private cdr:        ChangeDetectorRef,  // ✅ Ajout du ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.loading = true;
    this.error   = false;
    this.kpis    = [];

    this.username = this.auth.getUsername() ?? 'user';

    // Détection rôle depuis JWT — le backend met claim("role", "ROLE_ADMIN")
    const token = this.auth.getToken();
    if (token) {
      try {
        const p = JSON.parse(atob(token.split('.')[1]));
        this.isAdmin = p.role === 'ROLE_ADMIN';
      } catch {
        this.isAdmin = false;
      }
    }

    this.isAdmin ? this.loadAdmin() : this.loadDeveloper();
  }

  // ── Admin ─────────────────────────────────────────────────────────────────

  private loadAdmin(): void {
    forkJoin({
      stats: this.monitoring.getStats().pipe(
        catchError(err => { console.error('[Dashboard Admin] monitoring/stats:', err); return of(null); })
      ),
      logs: this.auditSvc.getAllLogs({}).pipe(
        catchError(err => { console.error('[Dashboard Admin] audit-logs/admin:', err); return of([] as AuditLog[]); })
      ),
    })
    .pipe(finalize(() => { 
      this.loading = false; 
      this.cdr.detectChanges(); // ✅ Forcer la détection après finalize
    }))
    .subscribe(({ stats, logs }) => {
      if (!stats) { 
        this.error = true; 
        this.cdr.detectChanges(); // ✅ Détecter le changement d'erreur
        return; 
      }
      const s = stats as MonitoringStatsResponse;
      this.buildAdminKpis(s);
      this.buildAdminChart(s);
      this.buildAdminActions(s);
      this.buildAdminBreakdowns(s);
      this.buildActivityFeed(logs as AuditLog[]);
      this.cdr.detectChanges(); // ✅ Détecter après toutes les mises à jour
    });
  }

  private buildAdminKpis(s: MonitoringStatsResponse): void {
    this.kpis = [
      { label: 'Total requests',   value: Number(s.totalRequests).toLocaleString('fr-FR'),      sub: 'All audited actions',  icon: 'swap_horiz',    color: 'default' },
      { label: 'Error rate',       value: Number(s.errorRatePct).toFixed(1) + ' %',             sub: 'HTTP requests ≥ 400',     icon: 'error_outline', color: s.errorRatePct > 5 ? 'danger' : 'info' },
      { label: 'Avg duration',     value: s.avgDurationMs != null ? Number(s.avgDurationMs).toFixed(0) + ' ms' : '—', sub: 'Average across all requests', icon: 'timer', color: 'info' },
      { label: 'Active pipelines', value: Number(s.configuredPipelines).toLocaleString('fr-FR'), sub: 'Configured + Validated',  icon: 'device_hub',    color: 'default' },
      { label: 'Payloads processed', value: Number(s.successfulPayloads).toLocaleString('fr-FR'),  sub: 'Status SENT / MAPPED',    icon: 'check_circle',  color: 'success' },
      { label: 'Failed payloads', value: Number(s.failedPayloads).toLocaleString('fr-FR'),      sub: 'Status FAILED',           icon: 'cancel',        color: Number(s.failedPayloads) > 0 ? 'danger' : 'default' },
    ];
    this.cdr.detectChanges(); // ✅ Détecter après mise à jour des KPIs
  }

  private buildAdminChart(s: MonitoringStatsResponse): void {
    // Le backend retourne hour au format "2026-06-24T19" — on extrait l'heure
    const toHourKey = (h: string): number => {
      if (!h) return -1;
      // "2026-06-24T19" → 19
      const isoMatch = h.match(/T(\d{1,2})$/);
      if (isoMatch) return parseInt(isoMatch[1], 10);
      // "19:00" → 19
      const colonMatch = h.match(/^(\d{1,2}):/);
      if (colonMatch) return parseInt(colonMatch[1], 10);
      // "19" → 19
      const num = parseInt(h, 10);
      return isNaN(num) ? -1 : num;
    };

    const reqMap = new Map(s.requestsByHour.map(b => [toHourKey(b.hour), Number(b.count)]));
    const errMap = new Map(s.errorsByHour.map(b => [toHourKey(b.hour), Number(b.count)]));

    this.hourly = Array.from({ length: 24 }, (_, i) => ({
      hour:     i + 'h',
      requests: reqMap.get(i) ?? 0,
      errors:   errMap.get(i) ?? 0,
    }));
    this.maxRequests = Math.max(...this.hourly.map(h => h.requests), 1);
    this.cdr.detectChanges(); // ✅ Détecter après mise à jour du graphique
  }

  private buildAdminActions(s: MonitoringStatsResponse): void {
    if (!s.requestsByAction?.length) return;
    const maxTotal = Math.max(...s.requestsByAction.map(a => Number(a.total)), 1);
    this.actions = s.requestsByAction.map(a => ({
      action: a.action,
      total:  Number(a.total),
      errors: Number(a.errors),
      pct:    Math.round(Number(a.total) / maxTotal * 100),
    }));
    this.cdr.detectChanges(); // ✅ Détecter après mise à jour des actions
  }

  private buildAdminBreakdowns(s: MonitoringStatsResponse): void {
    const total     = Math.max(Number(s.totalPipelines), 1);
    const validated = Number(s.configuredPipelines);
    const draft     = Math.max(total - validated, 0);
    this.pipelineBreakdown = [
      { status: 'VALIDATED', count: validated, pct: Math.round(validated / total * 100) },
      { status: 'DRAFT',     count: draft,     pct: Math.round(draft     / total * 100) },
    ];

    const ptotal  = Math.max(Number(s.totalPayloads), 1);
    const success = Number(s.successfulPayloads);
    const failed  = Number(s.failedPayloads);
    const pending = Math.max(ptotal - success - failed, 0);
    this.payloadBreakdown = [
      { status: 'SENT/MAPPED', count: success, pct: Math.round(success / ptotal * 100) },
      { status: 'FAILED',      count: failed,  pct: Math.round(failed  / ptotal * 100) },
      { status: 'PENDING',     count: pending, pct: Math.round(pending / ptotal * 100) },
    ];
    this.cdr.detectChanges(); // ✅ Détecter après mise à jour des breakdowns
  }

  private buildActivityFeed(logs: AuditLog[]): void {
    this.activityFeed = (logs ?? []).slice(0, 8).map(l => ({
      label:   l.details ?? `${l.action} — ${l.targetEntity}`,
      sub:     `${l.performedBy} · HTTP ${l.httpStatus ?? '—'}`,
      timeAgo: this.relativeTime(l.timestamp),
      outcome: l.httpStatus != null && l.httpStatus >= 400 ? 'error'
             : l.action === 'DELETE' ? 'warn' : 'ok',
    }));
    this.cdr.detectChanges(); // ✅ Détecter après mise à jour du feed
  }

  // ── Developer ─────────────────────────────────────────────────────────────

  private loadDeveloper(): void {
    forkJoin({
      pipes: this.pipeSvc.getMine().pipe(
        catchError(err => { console.error('[Dashboard Dev] pipelines/my:', err); return of([] as Pipeline[]); })
      ),
      logs: this.auditSvc.getMyLogs().pipe(
        catchError(err => { console.error('[Dashboard Dev] audit-logs/me:', err); return of([] as AuditLog[]); })
      ),
    })
    .subscribe({
      next: ({ pipes, logs }) => {
        this.myPipelines = pipes ?? [];
        this.buildDevKpis(this.myPipelines, logs ?? []);
        this.buildActivityFeed(logs ?? []);
        this.loadDevPayloadsAndSandbox(this.myPipelines);
        this.cdr.detectChanges(); // ✅ Détecter après premier chargement
      },
      error: err => {
        console.error('[Dashboard Dev] forkJoin erreur critique:', err);
        this.loading = false;
        this.error   = true;
        this.cdr.detectChanges(); // ✅ Détecter le changement d'erreur
      },
    });
  }

  private buildDevKpis(pipes: Pipeline[], logs: AuditLog[]): void {
    const validated  = pipes.filter(p => p.status === 'VALIDATED').length;
    const configured = pipes.filter(p => p.status === 'CONFIGURED').length;
    const sandboxLogs = logs.filter(l =>
      l.details?.toLowerCase().includes('sandbox') ||
      l.targetEntity?.toLowerCase().includes('sandbox')
    );
    const ok = sandboxLogs.filter(l => l.httpStatus == null || l.httpStatus < 400).length;
    const rate = sandboxLogs.length > 0 ? Math.round(ok / sandboxLogs.length * 100) : 0;

    this.kpis = [
      { label: 'My pipelines',      value: pipes.length.toString(),      sub: 'Created by me',         icon: 'device_hub',    color: 'default' },
      { label: 'Validated',            value: validated.toString(),          sub: 'Ready for production', icon: 'check_circle',  color: 'success' },
      { label: 'in configuration',   value: configured.toString(),         sub: 'Status CONFIGURED',     icon: 'settings',      color: 'info'    },
      { label: 'sandbox Tests',      value: sandboxLogs.length.toString(), sub: 'From my logs',       icon: 'science',       color: 'default' },
      { label: 'Validation rate', value: rate + ' %',                   sub: 'Sandbox passed ✓',      icon: 'trending_up',   color: rate >= 70 ? 'success' : 'danger' },
      { label: 'My actions',        value: logs.length.toString(),        sub: 'Personal log',     icon: 'history',       color: 'default' },
    ];
    this.cdr.detectChanges(); // ✅ Détecter après mise à jour des KPIs dev
  }

  private loadDevPayloadsAndSandbox(pipes: Pipeline[]): void {
    if (pipes.length === 0) {
      this.payloadBreakdown = [];
      this.sandboxRuns      = [];
      this.loading          = false;
      this.cdr.detectChanges(); // ✅ Détecter quand il n'y a pas de pipelines
      return;
    }

    const targets = pipes.slice(0, 3);

    const payloadCalls = targets.map(p =>
      this.payloadSvc.getPayloadsByPipeline(p.id).pipe(
        catchError(err => { console.error(`[Dashboard Dev] payloads/${p.id}:`, err); return of([] as PayloadResponse[]); })
      )
    );

    const sandboxCalls = targets.map(p =>
      this.sandboxSvc.getHistory(p.id, 0, 5).pipe(
        catchError(err => { console.error(`[Dashboard Dev] sandbox/logs/${p.id}:`, err); return of(null as SandboxLogsPage | null); })
      )
    );

    forkJoin([...payloadCalls, ...sandboxCalls])
      .pipe(finalize(() => { 
        this.loading = false; 
        this.cdr.detectChanges(); // ✅ Forcer la détection après finalize
      }))
      .subscribe(results => {
        const n           = targets.length;
        const allPayloads = (results.slice(0, n) as PayloadResponse[][]).flat();
        const allPages    = results.slice(n) as (SandboxLogsPage | null)[];

        this.buildDevPayloadBreakdown(allPayloads);

        this.sandboxRuns = allPages
          .filter((p): p is SandboxLogsPage => p !== null)
          .flatMap(p => p.content ?? [])
          .sort((a, b) => new Date(String(b.executedAt)).getTime() - new Date(String(a.executedAt)).getTime())
          .slice(0, 5);
        
        this.cdr.detectChanges(); // ✅ Détecter après mise à jour des payloads et sandbox
      });
  }

  private buildDevPayloadBreakdown(payloads: PayloadResponse[]): void {
    const counts: Record<string, number> = {};
    payloads.forEach(p => { counts[p.status] = (counts[p.status] ?? 0) + 1; });
    const total = payloads.length || 1;
    this.payloadBreakdown = Object.entries(counts).map(([status, count]) => ({
      status, count, pct: Math.round(count / total * 100),
    }));
    this.cdr.detectChanges(); // ✅ Détecter après mise à jour du breakdown payload
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private relativeTime(iso: string): string {
    if (!iso) return '—';
    const diff = Date.now() - new Date(iso).getTime();
    const m = Math.floor(diff / 60_000);
    if (m <  1) return 'just now';
    if (m < 60) return `${m} min ago`;
    const h = Math.floor(m / 60);
    if (h < 24) return `${h}h ago`;
    return `${Math.floor(h / 24)}d ago`;
  }

  sandboxRunOutcome(r: SandboxLogResponse): 'ok' | 'warn' | 'error' {
    if (!r.validationPassed)              return 'error';
    if (r.validationPassed && !r.mappingApplied) return 'warn';
    return 'ok';
  }

  sandboxRunLabel(r: SandboxLogResponse): string {
    if (!r.validationPassed) return 'Validation ✗';
    if (!r.mappingApplied)   return 'Validation ✓ · Mapping —';
    return 'Validation ✓ · Mapping ✓';
  }

  barH(v: number): string    { return Math.round(v / this.maxRequests * 100) + '%'; }
  errBarH(v: number): string { return Math.max(Math.round(v / this.maxRequests * 100 * 3), 1) + '%'; }

  pipelineTimeAgo(p: Pipeline): string      { return this.relativeTime(p.createdAt); }
  runTimeAgo(r: SandboxLogResponse): string { return this.relativeTime(String(r.executedAt)); }
}