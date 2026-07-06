import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { PipelineService } from '../../services/pipeline/pipeline-service';
import { ProviderService } from '../../services/provider/provider-service';
import { Pipeline, providerNames, firstProvider } from '../../models/pipeline';
import { Provider } from '../../models/provider';
import { PipelineFieldsTabComponent } from './tabs/pipeline-fields-tab/pipeline-fields-tab.component';
import { PipelinePayloadsTabComponent } from './tabs/pipeline-payloads-tab/pipeline-payloads-tab.component';
import { PipelineRulesTabComponent } from './tabs/pipeline-rules-tab/pipeline-rules-tab.component';
import { PipelineMappingTabComponent } from './tabs/pipeline-mapping-tab-component/pipeline-mapping-tab-component';
import { PipelineSandboxTabComponent } from '../sandbox/sandbox.component';
import { DockerImageButtonComponent } from './docker-image-button-component/docker-image-button-component';
import { PipelineProcessTabComponent } from './tabs/pipeline-process-tab-component/pipeline-process-tab-component';
import { PipelineResponseRulesTabComponent } from './tabs/pipeline-response-rules-tab-component/pipeline-response-rules-tab-component';

type DetailTab = 'info' | 'fields' | 'payloads' | 'rules' | 'mapping' | 'response-rules' | 'process' | 'image_docker';

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
    PipelineSandboxTabComponent,
    PipelineMappingTabComponent,
    DockerImageButtonComponent,
    PipelineProcessTabComponent,
    PipelineResponseRulesTabComponent,
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
  sandboxPipeline: Pipeline | null = null;
  form!: FormGroup;

  readonly FORMATS  = ['JSON', 'XML', 'CSV', 'PLAIN_TEXT'];
  readonly STATUSES = ['DRAFT', 'CONFIGURED', 'VALIDATED'];

  // Helpers multi-provider exposés au template
  readonly providerNames = providerNames;
  readonly firstProvider = firstProvider;

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
      // Multi-provider : tableau d'IDs (remplace le champ singulier providerId)
      providerIds:  [[]]
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
      (p.providers ?? []).some(pr => pr.name.toLowerCase().includes(q))
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
      name:         p.name,
      version:      p.version,
      inputFormat:  p.inputFormat,
      outputFormat: p.outputFormat,
      providerIds:  (p.providers ?? []).map(pr => pr.id)
    });
    this.showModal = true;
    this.cdr.detectChanges();
  }

  // ── Helpers pour la sélection multi-provider via checkboxes ───────────────

  /** Retourne true si le provider est dans la liste sélectionnée du formulaire. */
  isProviderSelected(providerId: number): boolean {
    const ids: number[] = this.form.get('providerIds')?.value ?? [];
    return ids.includes(providerId);
  }

  /** Coche / décoche un provider dans le FormControl providerIds. */
  toggleProvider(providerId: number, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    const current: number[] = [...(this.form.get('providerIds')?.value ?? [])];

    if (checked && !current.includes(providerId)) {
      current.push(providerId);
    } else if (!checked) {
      const idx = current.indexOf(providerId);
      if (idx > -1) current.splice(idx, 1);
    }

    this.form.get('providerIds')?.setValue(current);
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
  // Méthode
  openSandbox(p: Pipeline): void {
    this.sandboxPipeline = p;
    this.cdr.detectChanges();
  }

  closeSandbox(): void {
   this.sandboxPipeline = null;
   this.cdr.detectChanges();
 }

  trackById(_: number, p: Pipeline): number { return p.id; }
}