import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  ReactiveFormsModule,
  FormBuilder,
  FormGroup,
  Validators
} from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { PipelineService } from '../../services/pipeline/pipeline-service';
import { ProviderService } from '../../services/provider/provider-service';
import { Pipeline } from '../../models/pipeline';
import { Provider } from '../../models/provider';

@Component({
  selector: 'app-pipeline-admin',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  templateUrl: './pipeline-admin-component.html',
  styleUrls: ['./pipeline-admin-component.scss']
})
export class PipelineAdminComponent implements OnInit {

  pipelines: Pipeline[] = [];
  filtered:  Pipeline[] = [];
  providers: Provider[] = [];

  searchQuery  = '';
  filterStatus = '';
  filterFormat = '';

  showModal         = false;
  editingId: number | null = null;
  deletingPipeline: Pipeline | null = null;
  detailPipeline:   Pipeline | null = null;

  form!: FormGroup;

  readonly FORMATS  = ['JSON', 'XML', 'CSV', 'PLAIN_TEXT'];
  readonly STATUSES = ['DRAFT', 'CONFIGURED', 'VALIDATED'];

  constructor(
    private pipelineService: PipelineService,
    private providerService: ProviderService,
    private fb: FormBuilder,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.buildForm();
    this.loadPipelines();
    this.providerService.getAll().subscribe(data => {
      this.providers = data;
      this.cdr.detectChanges();
    });
  }

  private buildForm(): void {
    this.form = this.fb.group({
      name:         ['', Validators.required],
      version:      [''],
      inputFormat:  ['', Validators.required],
      outputFormat: ['', Validators.required],
      providerId:   [null]
    });
  }

  private loadPipelines(): void {
    this.pipelineService.getAll().subscribe(data => {
      this.pipelines = data;
      this.applyFilters();
      this.cdr.detectChanges();
    });
  }

  applyFilters(): void {
    let result = [...this.pipelines];
    const q = this.searchQuery.trim().toLowerCase();

    if (q) {
      result = result.filter(p =>
        p.name.toLowerCase().includes(q) ||
        p.createdBy.toLowerCase().includes(q) ||
        (p.providerName ?? '').toLowerCase().includes(q)
      );
    }
    if (this.filterStatus) {
      result = result.filter(p => p.status === this.filterStatus);
    }
    if (this.filterFormat) {
      result = result.filter(p =>
        p.inputFormat === this.filterFormat ||
        p.outputFormat === this.filterFormat
      );
    }
    this.filtered = result;
    this.cdr.detectChanges();
  }

  // ── Stats ──────────────────────────────────────────────────────────────────
  countByStatus(status: string): number {
    return this.pipelines.filter(p => p.status === status).length;
  }

  // ── Modal ──────────────────────────────────────────────────────────────────
  openCreate(): void {
    this.editingId = null;
    this.form.reset();
    this.showModal = true;
    this.cdr.detectChanges();
  }

  openEdit(p: Pipeline): void {
    this.editingId = p.id;
    this.form.patchValue({
      name:         p.name,
      version:      p.version,
      inputFormat:  p.inputFormat,
      outputFormat: p.outputFormat,
      providerId:   p.providerId ?? null
    });
    this.showModal = true;
    this.cdr.detectChanges();
  }

  closeModal(): void {
    this.showModal = false;
    this.editingId = null;
    this.cdr.detectChanges();
  }

  save(): void {
    if (this.form.invalid) return;
    const val = this.form.value;

    if (this.editingId !== null) {
      this.pipelineService.update(this.editingId, val).subscribe(() => {
        this.loadPipelines();
        this.closeModal();
        this.cdr.detectChanges();
      });
    } else {
      this.pipelineService.create(val).subscribe(() => {
        this.loadPipelines();
        this.closeModal();
        this.cdr.detectChanges();
      });
    }
  }

  // ── Delete ─────────────────────────────────────────────────────────────────
  askDelete(p: Pipeline): void {
    this.deletingPipeline = p;
    this.cdr.detectChanges();
  }

  cancelDelete(): void {
    this.deletingPipeline = null;
    this.cdr.detectChanges();
  }

  confirmDelete(): void {
    if (!this.deletingPipeline) return;
    this.pipelineService.delete(this.deletingPipeline.id).subscribe(() => {
      this.deletingPipeline = null;
      this.loadPipelines();
      this.cdr.detectChanges();
    });
  }

  // ── Detail ─────────────────────────────────────────────────────────────────
  openDetail(p: Pipeline): void {
    this.detailPipeline = p;
    this.cdr.detectChanges();
  }

  closeDetail(): void {
    this.detailPipeline = null;
    this.cdr.detectChanges();
  }
}