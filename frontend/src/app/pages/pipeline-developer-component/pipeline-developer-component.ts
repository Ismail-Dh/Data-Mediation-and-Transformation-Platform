import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { PipelineService } from '../../services/pipeline/pipeline-service';
import { ProviderService } from '../../services/provider/provider-service';
import { Pipeline } from '../../models/pipeline';
import { Provider } from '../../models/provider';
import { PipelineFieldsTabComponent } from './tabs/pipeline-fields-tab/pipeline-fields-tab.component';
import { PipelinePayloadsTabComponent } from './tabs/pipeline-payloads-tab/pipeline-payloads-tab.component';
import { PipelineRulesTabComponent } from './tabs/pipeline-rules-tab/pipeline-rules-tab.component';
import { PipelineMappingTabComponent } from './tabs/pipeline-mapping-tab-component/pipeline-mapping-tab-component';

type DetailTab = 'info' | 'fields' | 'payloads' | 'rules' | 'mapping';

@Component({
  selector: 'app-pipeline-developer',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    PipelineFieldsTabComponent,
    PipelinePayloadsTabComponent,
    PipelineRulesTabComponent,
    PipelineMappingTabComponent
  ],
  templateUrl: './pipeline-developer-component.html',
  styleUrls: ['./pipeline-developer-component.scss']
})
export class PipelineDeveloperComponent implements OnInit {

  pipelines: Pipeline[] = [];
  filtered:  Pipeline[] = [];
  providers: Provider[] = [];

  searchQuery  = '';
  filterStatus = '';

  showModal         = false;
  editingId: number | null = null;
  deletingPipeline: Pipeline | null = null;
  detailPipeline:   Pipeline | null = null;
  detailTab: DetailTab = 'info';

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
    this.pipelineService.getMine().subscribe(data => {
      this.pipelines = data;
      this.applyFilters();
      this.cdr.detectChanges();
    });
  }

  applyFilters(): void {
    let result = [...this.pipelines];
    const q = this.searchQuery.trim().toLowerCase();
    if (q) result = result.filter(p =>
      p.name.toLowerCase().includes(q) ||
      (p.providerName ?? '').toLowerCase().includes(q)
    );
    if (this.filterStatus) result = result.filter(p => p.status === this.filterStatus);
    this.filtered = result;
    this.cdr.detectChanges();
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
      name: p.name, version: p.version,
      inputFormat: p.inputFormat, outputFormat: p.outputFormat,
      providerId: p.providerId ?? null
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
        this.loadPipelines(); this.closeModal();
      });
    } else {
      this.pipelineService.create(val).subscribe(() => {
        this.loadPipelines(); this.closeModal();
      });
    }
  }

  // ── Delete ─────────────────────────────────────────────────────────────────
  askDelete(p: Pipeline): void { this.deletingPipeline = p; this.cdr.detectChanges(); }
  cancelDelete(): void { this.deletingPipeline = null; this.cdr.detectChanges(); }

  confirmDelete(): void {
    if (!this.deletingPipeline) return;
    this.pipelineService.delete(this.deletingPipeline.id).subscribe(() => {
      this.deletingPipeline = null;
      this.loadPipelines();
    });
  }

  // ── Detail ─────────────────────────────────────────────────────────────────
  openDetail(p: Pipeline): void {
    this.detailPipeline = p;
    this.detailTab = 'info';
    this.cdr.detectChanges();
  }

  closeDetail(): void {
    this.detailPipeline = null;
    this.cdr.detectChanges();
  }

  setDetailTab(tab: DetailTab): void {
    this.detailTab = tab;
    this.cdr.detectChanges();
  }

  trackById(_: number, p: Pipeline): number { return p.id; }
}