import {
  Component, OnInit, OnDestroy,
  signal, inject, computed, ChangeDetectorRef
} from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { MonitoringService } from '../../services/monitoring/monitoring.service';
import { MonitoringStatsResponse, ActionStat, HourlyBucket } from '../../models/monitoring.model'

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

  stats   = signal<MonitoringStatsResponse | null>(null);
  loading = signal(true);
  error   = signal<string | null>(null);

  private refreshInterval: any;

  // ── Computed helpers ──────────────────────────────────────────────────────

  pipelineUsageRate = computed(() => {
    const s = this.stats();
    if (!s || s.totalPipelines === 0) return 0;
    return Math.round((s.configuredPipelines / s.totalPipelines) * 100);
  });

  payloadSuccessRate = computed(() => {
    const s = this.stats();
    if (!s || s.totalPayloads === 0) return 0;
    return Math.round((s.successfulPayloads / s.totalPayloads) * 100);
  });

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

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  ngOnInit() {
    this.load();
    // auto-refresh every 30s
    this.refreshInterval = setInterval(() => this.load(), 30_000);
  }

  ngOnDestroy() {
    clearInterval(this.refreshInterval);
  }

  load() {
    this.loading.set(true);
    this.error.set(null);
    this.monitoringService.getStats().subscribe({
      next: data => {
        this.stats.set(data);
        this.loading.set(false);
        this.cdr.detectChanges();
      },
      error: () => {
        this.error.set('Impossible de charger les statistiques.');
        this.loading.set(false);
        this.cdr.detectChanges();
      }
    });
  }

  // ── Chart helpers ─────────────────────────────────────────────────────────

  barHeight(count: number, max: number): number {
    return max === 0 ? 0 : Math.round((count / max) * 100);
  }

  formatHour(hour: string): string {
    // hour format from backend: "2024-01-15 14" → "14h"
    const parts = hour.split(' ');
    return parts.length > 1 ? `${parts[1]}h` : hour;
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

  trackByHour(_: number, b: HourlyBucket) { return b.hour; }
  trackByAction(_: number, a: ActionStat) { return a.action; }
}