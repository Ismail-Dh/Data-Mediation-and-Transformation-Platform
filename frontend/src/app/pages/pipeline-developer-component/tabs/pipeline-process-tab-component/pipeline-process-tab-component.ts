import { Component, Input, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ProcessApiService } from '../../../../../app/services/process/process-api-service';
import {
  ProcessResponse,
  ProviderResponseDetail,
} from '../../../../models/process.model';

type ProcessTab = 'summary' | 'providers' | 'aggregated';

@Component({
  selector: 'app-pipeline-process-tab',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './pipeline-process-tab-component.html',
  styleUrl: './pipeline-process-tab-component.scss',
})
export class PipelineProcessTabComponent implements OnInit {

  @Input() pipelineId!: number;

  rawContent  = '{\n  "amount": 250\n}';
  inputFormat = 'JSON';
  readonly FORMATS = ['JSON', 'XML', 'CSV'];

  loading = false;
  error:  string | null = null;
  result: ProcessResponse | null = null;

  activeTab: ProcessTab = 'summary';
  expandedProviders = new Set<number>();

  constructor(
    private processApi: ProcessApiService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {}

  runProcess(): void {
    if (!this.rawContent.trim()) return;
    this.loading = true;
    this.error   = null;
    this.result  = null;
    this.expandedProviders.clear();

    this.processApi.runProcess({
      pipelineId:  this.pipelineId,
      rawContent:  this.rawContent,
      inputFormat: this.inputFormat,
    }).subscribe({
      next: res => {
        this.result  = res;
        this.loading = false;
        this.activeTab = 'summary';
        this.cdr.detectChanges();
      },
      error: err => {
        this.error   = err?.error?.message ?? err?.message ?? 'An error occurred';
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }

  setTab(tab: ProcessTab): void {
    this.activeTab = tab;
    this.cdr.detectChanges();
  }

  toggleProvider(idx: number): void {
    this.expandedProviders.has(idx)
      ? this.expandedProviders.delete(idx)
      : this.expandedProviders.add(idx);
    this.cdr.detectChanges();
  }

  isExpanded(idx: number): boolean {
    return this.expandedProviders.has(idx);
  }

  statusClass(d: ProviderResponseDetail): string {
    if (!d.dispatchSuccess)  return 'badge--failed';
    if (!d.validationPassed) return 'badge--warning';
    return 'badge--validated';
  }

  statusLabel(d: ProviderResponseDetail): string {
    if (!d.dispatchSuccess)  return 'Dispatch failed';
    if (!d.validationPassed) return 'Validation KO';
    return 'OK';
  }

  httpBadge(status: number): string {
    if (status === 0)    return 'badge--failed';
    if (status < 300)    return 'badge--validated';
    if (status < 400)    return 'badge--draft';
    return 'badge--failed';
  }

  json(obj: unknown): string {
    return JSON.stringify(obj, null, 2);
  }

  objectEntries(obj: Record<string, unknown>): [string, unknown][] {
    return Object.entries(obj);
  }
}