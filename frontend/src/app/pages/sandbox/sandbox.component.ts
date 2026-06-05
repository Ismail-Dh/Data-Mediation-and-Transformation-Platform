import { Component, Input, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SandboxResponse } from '../../models/sandbox';
import { SandboxService } from '../../services/sandbox/sandbox.service';
import { PipelineService } from '../../services/pipeline/pipeline-service';
import { Pipeline,PipelineStatus  } from '../../models/pipeline';

@Component({
  selector: 'app-pipeline-sandbox-tab',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './sandbox.component.html',  
  styleUrl: './sandbox.component.scss'   
})
export class PipelineSandboxTabComponent {

  @Input() pipelineId!: number;
  @Input() pipeline!: Pipeline;

  rawContent = '';
  format     = 'JSON';
  loading    = false;

  result:    SandboxResponse | null = null;
  error:     string | null = null;

  // validate / revert
  statusLoading = false;
  statusError:  string | null = null;

  readonly FORMATS = ['JSON'];

  constructor(
    private sandboxService:  SandboxService,
    private pipelineService: PipelineService,
    private cdr: ChangeDetectorRef
  ) {}

  // ── Run sandbox ───────────────────────────────────────────────────────────

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
        // Mettre à jour le statut affiché dans le pipeline local
        this.pipeline.status = res.pipelineStatus as PipelineStatus;
        this.cdr.detectChanges();
      },
      error: err => {
        this.error   = err?.error?.message ?? 'Server error.';
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  // ── Validate pipeline manually ────────────────────────────────────────────

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

  // ── Revert pipeline to CONFIGURED ────────────────────────────────────────

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

  // ── Helpers ───────────────────────────────────────────────────────────────

  get canValidate(): boolean { return this.pipeline?.status === 'CONFIGURED'; }
  get canRevert():   boolean { return this.pipeline?.status === 'VALIDATED'; }
}