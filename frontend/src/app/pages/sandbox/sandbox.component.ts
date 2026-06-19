import { Component, Input, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  SandboxResponse, SandboxLogResponse, SandboxLogsPage,
  ParsedSandboxLog, FieldViolation, MappingRuleSummary
} from '../../models/sandbox';
import { SandboxService }  from '../../services/sandbox/sandbox.service';
import { PipelineService } from '../../services/pipeline/pipeline-service';
import { Pipeline, PipelineStatus } from '../../models/pipeline';

@Component({
  selector: 'app-pipeline-sandbox-tab',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './sandbox.component.html',
  styleUrl: './sandbox.component.scss'
})
export class PipelineSandboxTabComponent implements OnInit {

  @Input() pipelineId!: number;
  @Input() pipeline!:   Pipeline;

  rawContent = '';
  format     = 'JSON';
  loading    = false;
  result:    SandboxResponse | null = null;
  error:     string | null = null;

  statusLoading = false;
  statusError:  string | null = null;

  logs:           SandboxLogResponse[] = [];
  logsLoading     = false;
  logsPage        = 0;
  logsPageSize    = 10;
  logsTotalPages  = 0;
  logsTotalItems  = 0;
  showLogs        = false;

  selectedLog: ParsedSandboxLog | null = null;
  activeTab:   'payload' | 'violations' | 'mapping' = 'payload';

  readonly FORMATS = ['JSON'];

  constructor(
    private sandboxService:  SandboxService,
    private pipelineService: PipelineService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void { this.loadLogs(); }

  // ── Run ───────────────────────────────────────────────────────────────────

  run(): void {
    if (!this.rawContent.trim()) return;
    this.loading = true;
    this.result  = null;
    this.error   = null;

    this.sandboxService.run(this.pipelineId, {
      rawContent: this.rawContent,
      format:     this.format
    }).subscribe({
      next: res => {
        this.result  = res;
        this.loading = false;
        this.pipeline.status = res.pipelineStatus as PipelineStatus;
        this.loadLogs();
        this.cdr.detectChanges();
      },
      error: err => {
        this.error   = err?.error?.message ?? 'Server error.';
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  // ── Validate / Revert ─────────────────────────────────────────────────────

  validate(): void {
    this.statusLoading = true;
    this.statusError   = null;
    this.pipelineService.validatePipeline(this.pipelineId).subscribe({
      next: updated => {
        this.pipeline.status = updated.status as PipelineStatus;
        this.statusLoading   = false;
        this.cdr.detectChanges();
      },
      error: err => {
        this.statusError   = err?.error?.message ?? 'Cannot validate pipeline.';
        this.statusLoading = false;
        this.cdr.detectChanges();
      }
    });
  }

  revert(): void {
    this.statusLoading = true;
    this.statusError   = null;
    this.pipelineService.revertPipeline(this.pipelineId).subscribe({
      next: updated => {
        this.pipeline.status = updated.status as PipelineStatus;
        this.statusLoading   = false;
        this.cdr.detectChanges();
      },
      error: err => {
        this.statusError   = err?.error?.message ?? 'Cannot revert pipeline.';
        this.statusLoading = false;
        this.cdr.detectChanges();
      }
    });
  }

  // ── Historique ────────────────────────────────────────────────────────────

  loadLogs(): void {
    this.logsLoading = true;
    this.sandboxService.getLogs(this.pipelineId, this.logsPage, this.logsPageSize)
      .subscribe({
        next: page => {
          this.logs           = page.content;
          this.logsTotalPages = page.totalPages;
          this.logsTotalItems = page.totalElements;
          this.logsLoading    = false;
          this.cdr.detectChanges();
        },
        error: () => {
          this.logsLoading = false;
          this.cdr.detectChanges();
        }
      });
  }

  prevPage(): void {
    if (this.logsPage > 0) { this.logsPage--; this.loadLogs(); }
  }

  nextPage(): void {
    if (this.logsPage < this.logsTotalPages - 1) { this.logsPage++; this.loadLogs(); }
  }

  toggleLogs(): void { this.showLogs = !this.showLogs; }

  // ── Modal ─────────────────────────────────────────────────────────────────

  openLog(log: SandboxLogResponse): void {
    this.selectedLog = {
      log,
      violations:      this.parseJson<FieldViolation[]>(log.violations)       ?? [],
      originalPayload: this.parseJson<Record<string, unknown>>(log.originalPayload),
      mappedPayload:   this.parseJson<Record<string, unknown>>(log.mappedPayload),
      mappingSummary:  this.parseJson<MappingRuleSummary[]>(log.mappingSummary) ?? []
    };
    this.activeTab = !log.validationPassed ? 'violations' : 'mapping';
    this.cdr.detectChanges();
  }

  closeModal(): void {
    this.selectedLog = null;
    this.cdr.detectChanges();
  }

  setTab(tab: 'payload' | 'violations' | 'mapping'): void {
    this.activeTab = tab;
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  get canValidate(): boolean { return this.pipeline?.status === 'CONFIGURED'; }
  get canRevert():   boolean { return this.pipeline?.status === 'VALIDATED'; }

  formatDuration(ms: number | null): string {
    if (ms === null) return '—';
    return ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(1)}s`;
  }

  formatDate(iso: string): string {
    return new Date(iso).toLocaleString();
  }

  formatJson(obj: unknown): string {
    return JSON.stringify(obj, null, 2);
  }

  mappingTypeLabel(type: string): string {
    const labels: Record<string, string> = {
      FIELD_PLACEMENT:  'Field Placement',
      VALUE_TRANSFORM:  'Value Transform',
      RESTRUCTURING:    'Restructuring',
      FORMAT_CHANGE:    'Format Change',
      CALCULATED_FIELD: 'Calculated Field'
    };
    return labels[type] ?? type;
  }

  errorTypeLabel(code: string): string {
    const labels: Record<string, string> = {
      MISSING_FIELD:    'Missing field',
      TYPE_MISMATCH:    'Type mismatch',
      NULL_NOT_ALLOWED: 'Null not allowed',
      INVALID_JSON:     'Invalid JSON',
      INVALID_FORMAT:   'Invalid format'
    };
    return labels[code] ?? code;
  }

  private parseJson<T>(str: string | null): T | null {
    if (!str) return null;
    try { return JSON.parse(str) as T; }
    catch { return null; }
  }
}