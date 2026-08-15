import {
  Component, OnInit, OnDestroy,
  signal, inject, computed, ChangeDetectorRef
} from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { Subject, timer, of } from 'rxjs';
import { switchMap, takeUntil, tap, catchError } from 'rxjs/operators';
import { MonitoringService } from '../../services/monitoring/monitoring.service';
import { MonitoringStatsResponse, ActionStat, HourlyBucket, RoleStat, ErrorStat } from '../../models/monitoring.model'

/** Auto-refresh interval for real-time platform monitoring. */
const REFRESH_INTERVAL_MS = 30_000;

@Component({
  selector: 'app-monitoring',
  standalone: true,
  imports: [CommonModule, DecimalPipe],
  templateUrl: './monitoring.component.html',
  styleUrl: './monitoring.component.scss'
})
export class MonitoringComponent implements OnInit, OnDestroy {
  private monitoringService = inject(MonitoringService);
  private cdr               = inject(ChangeDetectorRef);

  stats       = signal<MonitoringStatsResponse | null>(null);
  loading     = signal(true);
  error       = signal<string | null>(null);
  lastUpdated = signal<Date | null>(null);

  private destroy$ = new Subject<void>();
  /** Manually triggered refreshes (button click) go through this subject too. */
  private manualRefresh$ = new Subject<void>();

  // ── Computed helpers ──────────────────────────────────────────────────────

  // Max value for bar chart scaling
  requestsChartMax = computed(() => {
    const s = this.stats();
    if (!s || s.requestsByHour.length === 0) return 1;
    return Math.max(...s.requestsByHour.map(b => b.count), 1);
  });

  errorsChartMax = computed(() => {
    const s = this.stats();
    if (!s || s.errorsByHour.length === 0) return 1;
    return Math.max(...s.errorsByHour.map(b => b.count), 1);
  });

  roleTotal = computed(() => {
    const s = this.stats();
    if (!s || !s.requestsByRole?.length) return 1;
    return Math.max(s.requestsByRole.reduce((sum, r) => sum + Number(r.count), 0), 1);
  });

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  ngOnInit() {
    // Real-time auto-refresh: fires immediately, then every REFRESH_INTERVAL_MS.
    // A manual "Refresh" click restarts the same pipeline so it never overlaps
    // with a pending automatic tick.
    this.manualRefresh$
      .pipe(
        takeUntil(this.destroy$),
        tap(() => { this.loading.set(true); this.error.set(null); }),
        switchMap(() => timer(0, REFRESH_INTERVAL_MS)),
        switchMap(() => this.fetchStats()),
      )
      .subscribe();

    this.manualRefresh$.next();
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /** Manual refresh button — restarts the auto-refresh timer from now. */
  load() {
    this.manualRefresh$.next();
  }

  private fetchStats() {
    this.loading.set(true);
    return this.monitoringService.getStats().pipe(
      tap(data => {
        this.stats.set(data);
        this.loading.set(false);
        this.lastUpdated.set(new Date());
        this.error.set(null);
        this.cdr.detectChanges();
      }),
      catchError(() => {
        this.error.set('Unable to load statistics.');
        this.loading.set(false);
        this.cdr.detectChanges();
        return of(null);
      }),
    );
  }

  // ── Chart helpers ─────────────────────────────────────────────────────────

  barHeight(count: number, max: number): number {
    return max === 0 ? 0 : Math.round((count / max) * 100);
  }

  formatHour(hour: string): string {
    // Backend format: "2026-08-10T14" (ISO date truncated to hour, "T" separator).
    // Also tolerate a space separator or a bare "14" just in case.
    if (!hour) return hour;
    const isoMatch = hour.match(/T(\d{1,2})$/);
    if (isoMatch) return `${isoMatch[1]}h`;
    const spaceParts = hour.split(' ');
    if (spaceParts.length > 1) return `${spaceParts[1]}h`;
    return /^\d{1,2}$/.test(hour) ? `${hour}h` : hour;
  }

  /** Human-readable "time ago" for the last recorded platform activity. */
  lastActivityAgo(): string | null {
    const s = this.stats();
    if (!s?.lastActivityAt) return null;
    const diffMs = Date.now() - new Date(s.lastActivityAt).getTime();
    const m = Math.floor(diffMs / 60_000);
    if (m < 1)  return 'just now';
    if (m < 60) return `${m} min ago`;
    const h = Math.floor(m / 60);
    if (h < 24) return `${h}h ago`;
    return `${Math.floor(h / 24)}d ago`;
  }

  getActionErrorRate(stat: ActionStat): number {
    if (stat.total === 0) return 0;
    return Math.round((stat.errors / stat.total) * 100);
  }

  getActionClass(stat: ActionStat): string {
    const rate = this.getActionErrorRate(stat);
    if (rate === 0)   return 'action-ok';
    if (rate < 10)    return 'action-warn';
    return 'action-error';
  }

  rolePct(role: RoleStat): number {
    return Math.round((Number(role.count) / this.roleTotal()) * 100);
  }

  formatRole(role: string): string {
    return role.replace(/^ROLE_/, '');
  }

  trackByHour(_: number, b: HourlyBucket) { return b.hour; }
  trackByAction(_: number, a: ActionStat) { return a.action; }
  trackByRole(_: number, r: RoleStat) { return r.role; }
  trackByError(_: number, e: ErrorStat) { return e.code; }
}
